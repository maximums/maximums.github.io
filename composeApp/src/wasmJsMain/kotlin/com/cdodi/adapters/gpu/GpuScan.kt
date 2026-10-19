package com.cdodi.adapters.gpu

import com.cdodi.webgpu.bindings.GPUAutoLayoutMode
import com.cdodi.webgpu.bindings.GPUBindGroup
import com.cdodi.webgpu.bindings.GPUBindGroupDescriptor
import com.cdodi.webgpu.bindings.GPUBindGroupEntry
import com.cdodi.webgpu.bindings.GPUBuffer
import com.cdodi.webgpu.bindings.GPUBufferBinding
import com.cdodi.webgpu.bindings.GPUBufferDescriptor
import com.cdodi.webgpu.bindings.GPUBufferUsage
import com.cdodi.webgpu.bindings.GPUCommandEncoder
import com.cdodi.webgpu.bindings.GPUComputePassDescriptor
import com.cdodi.webgpu.bindings.GPUComputePassTimestampWrites
import com.cdodi.webgpu.bindings.GPUComputePipeline
import com.cdodi.webgpu.bindings.GPUComputePipelineDescriptor
import com.cdodi.webgpu.bindings.GPUProgrammableStage
import com.cdodi.webgpu.bindings.auto
import com.cdodi.webgpu.context.GpuContext
import com.cdodi.webgpu.resource.resourceScope
import com.cdodi.webgpu.resource.Uint32Staging
import com.cdodi.webgpu.shader.compileShader

/**
 * Exclusive prefix sums of `u32` buffers on the GPU (`shaders/common/scan.wgsl`, Blelloch): each 512-value block is
 * scanned in shared memory, the block totals are scanned the same way, and each value adds its block's offset. Two
 * levels cover up to [MAX_COUNT] values.
 *
 * The counts are written with `queue.writeBuffer`, so every scan recorded into one submission must have the same
 * count (the last one written wins).
 */
class GpuScan private constructor(
    private val gpu: GpuContext,
    private val scanBlocks: GPUComputePipeline,
    private val addBlockSums: GPUComputePipeline,
) : AutoCloseable {

    private val resources = gpu.resourceScope()
    private val blockSums = storage(MAX_BLOCKS, "scan block sums")
    private val scannedBlockSums = storage(MAX_BLOCKS, "scan block offsets")
    private val unusedTotal = storage(4, "scan total") // the block totals' own total, never read
    private val countParams = uniform("scan count")
    private val blockParams = uniform("scan blocks")
    private val staging = Uint32Staging(4)
    private val bindGroups = mutableMapOf<Pair<GPUBuffer, GPUBuffer>, List<GPUBindGroup>>()

    /**
     * Records the exclusive scan of the first [count] values of [input] into [output] (a different buffer).
     *
     * @param span measures the whole scan: it starts in the first pass and ends in the last.
     */
    fun encode(encoder: GPUCommandEncoder, input: GPUBuffer, output: GPUBuffer, count: Int, span: GpuProfiler.Span? = null) {
        require(count in 1..MAX_COUNT) { "can scan 1..$MAX_COUNT values, not $count" }
        val blocks = (count + BLOCK - 1) / BLOCK
        gpu.queue.writeBuffer(countParams, 0.0, staging.fill(intArrayOf(count, 0, 0, 0)))
        gpu.queue.writeBuffer(blockParams, 0.0, staging.fill(intArrayOf(blocks, 0, 0, 0)))

        val (blocksGroup, totalsGroup, addGroup) = bindGroups.getOrPut(input to output) { bindGroupsFor(input, output) }
        val single = blocks == 1
        dispatch(encoder, scanBlocks, blocksGroup, blocks, "scan blocks", span?.compute(first = true, last = single))
        if (!single) {
            dispatch(encoder, scanBlocks, totalsGroup, 1, "scan block totals")
            dispatch(encoder, addBlockSums, addGroup, (count + THREADS - 1) / THREADS, "scan add-back", span?.compute(first = false, last = true))
        }
    }

    override fun close() = resources.close()

    private fun dispatch(
        encoder: GPUCommandEncoder,
        pipeline: GPUComputePipeline,
        group: GPUBindGroup,
        workgroups: Int,
        label: String,
        timestamps: GPUComputePassTimestampWrites? = null,
    ) {
        encoder.beginComputePass(GPUComputePassDescriptor(label = label, timestampWrites = timestamps)).apply {
            setPipeline(pipeline)
            setBindGroup(0, group)
            dispatchWorkgroups(workgroups)
            end()
        }
    }

    private fun bindGroupsFor(input: GPUBuffer, output: GPUBuffer): List<GPUBindGroup> = listOf(
        bindGroup(scanBlocks, countParams, input, output, blockSums),
        bindGroup(scanBlocks, blockParams, blockSums, scannedBlockSums, unusedTotal),
        bindGroup(addBlockSums, countParams, scannedBlockSums, output),
    )

    private fun bindGroup(pipeline: GPUComputePipeline, vararg buffers: GPUBuffer): GPUBindGroup = gpu.device.createBindGroup(
        GPUBindGroupDescriptor(
            layout = pipeline.getBindGroupLayout(0),
            entries = buffers.mapIndexed { binding, buffer -> GPUBindGroupEntry(binding, GPUBufferBinding(buffer)) },
        )
    )

    private fun storage(values: Int, label: String) =
        resources.buffer(GPUBufferDescriptor(size = values * 4.0, usage = GPUBufferUsage.STORAGE, label = label))

    private fun uniform(label: String) =
        resources.buffer(GPUBufferDescriptor(size = 16.0, usage = GPUBufferUsage.UNIFORM or GPUBufferUsage.COPY_DST, label = label))

    companion object {
        private const val BLOCK = 512
        private const val THREADS = 256
        private const val MAX_BLOCKS = BLOCK
        const val MAX_COUNT = BLOCK * MAX_BLOCKS

        /** Compiles `scan.wgsl` ([code]). */
        suspend fun create(gpu: GpuContext, code: String): GpuScan {
            val module = gpu.compileShader(code, label = "scan")
            fun pipeline(entryPoint: String) = gpu.device.createComputePipeline(
                GPUComputePipelineDescriptor(compute = GPUProgrammableStage(module, entryPoint), layout = GPUAutoLayoutMode.auto, label = "scan $entryPoint")
            )
            return GpuScan(gpu, pipeline("scanBlocks"), pipeline("addBlockSums"))
        }
    }
}
