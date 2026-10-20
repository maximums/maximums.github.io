package com.cdodi.adapters.gpu.model

import com.cdodi.gltf.Model
import com.cdodi.math.Bounds
import com.cdodi.math.Mat4
import com.cdodi.math.Vec3
import com.cdodi.webgpu.bindings.GPUBufferDescriptor
import com.cdodi.webgpu.bindings.GPUBufferUsage
import com.cdodi.webgpu.bindings.GPUExtent3DDict
import com.cdodi.webgpu.bindings.GPUMapMode
import com.cdodi.webgpu.bindings.GPUTexelCopyBufferInfo
import com.cdodi.webgpu.bindings.GPUTexelCopyBufferLayout
import com.cdodi.webgpu.bindings.GPUTexelCopyTextureInfo
import com.cdodi.webgpu.bindings.GPUTexture
import com.cdodi.webgpu.bindings.GPUTextureDescriptor
import com.cdodi.webgpu.bindings.GPUTextureFormat
import com.cdodi.webgpu.bindings.GPUTextureUsage
import com.cdodi.webgpu.bindings.mapAsyncSuspend
import com.cdodi.webgpu.bindings.rgba8unorm
import com.cdodi.webgpu.bindings.rgba8unormSrgb
import com.cdodi.webgpu.context.GpuContext
import com.cdodi.webgpu.context.requestGpuContext
import kotlinx.coroutines.test.runTest
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** The model layer on a real device: mip levels, images through the browser's decoder, a model's buffers. */
class ModelGpuTest {

    private suspend fun gpuOrSkip(what: String): GpuContext? =
        requestGpuContext().also { if (it == null) println("ModelGpuTest.$what: no WebGPU adapter in this browser — skipped") }

    /** [level] of [texture] ([width] × [height] there), its RGBA bytes row by row. */
    private suspend fun read(gpu: GpuContext, texture: GPUTexture, level: Int, width: Int, height: Int): List<Int> {
        val rowBytes = 256 // copies go row by row, each row 256-byte aligned
        val buffer = gpu.device.createBuffer(GPUBufferDescriptor(size = rowBytes * height.toDouble(), usage = GPUBufferUsage.COPY_DST or GPUBufferUsage.MAP_READ))
        val encoder = gpu.device.createCommandEncoder()
        encoder.copyTextureToBuffer(GPUTexelCopyTextureInfo(texture, mipLevel = level), GPUTexelCopyBufferInfo(buffer, bytesPerRow = rowBytes), GPUExtent3DDict(width = width, height = height))
        gpu.queue.submit(listOf(encoder.finish()).toJsArray())
        buffer.mapAsyncSuspend(GPUMapMode.READ)
        val bytes = Uint8Array(buffer.getMappedRange().unsafeCast<ArrayBuffer>())
        val out = (0 until height).flatMap { y -> (0 until width * 4).map { bytes[y * rowBytes + it].toInt() and 0xFF } }
        buffer.destroy()
        return out
    }

    private fun texture(gpu: GpuContext, format: GPUTextureFormat, side: Int, pixels: List<Int>): GPUTexture {
        val texture = gpu.device.createTexture(
            GPUTextureDescriptor(
                size = GPUExtent3DDict(width = side, height = side),
                mipLevelCount = Mipmaps.levelsFor(side, side),
                format = format,
                usage = Mipmaps.USAGE or GPUTextureUsage.COPY_DST or GPUTextureUsage.COPY_SRC,
            )
        )
        val data = Uint8Array(pixels.size).also { array -> pixels.forEachIndexed { i, v -> array[i] = v.toByte() } }
        gpu.queue.writeTexture(GPUTexelCopyTextureInfo(texture), data, GPUTexelCopyBufferLayout(bytesPerRow = side * 4), GPUExtent3DDict(width = side, height = side))
        return texture
    }

    private fun assertNear(expected: List<Int>, actual: List<Int>, tolerance: Int, what: String) {
        assertEquals(expected.size, actual.size, what)
        assertTrue(expected.indices.all { abs(expected[it] - actual[it]) <= tolerance }, "$what: expected $expected, was $actual")
    }

    @Test
    fun eachLevelAveragesTheOneAbove() = runTest(timeout = 60.seconds) {
        val gpu = gpuOrSkip("eachLevelAveragesTheOneAbove") ?: return@runTest
        gpu.use {
            // 4 × 4: red follows x, green follows y, blue both, alpha full.
            val pixels = (0 until 4).flatMap { y -> (0 until 4).flatMap { x -> listOf(x * 60, y * 60, (x + y) * 30, 255) } }
            val texture = texture(gpu, GPUTextureFormat.rgba8unorm, 4, pixels)
            assertEquals(3, Mipmaps.levelsFor(4, 4))
            Mipmaps.create(gpu).generate(texture, GPUTextureFormat.rgba8unorm, 3)
            // Each 2 × 2 block: x of 0 and 1 averages to 0.5 (× 60 = 30), 2 and 3 to 2.5 (150).
            assertNear(listOf(30, 30, 30, 255, 150, 30, 90, 255, 30, 150, 90, 255, 150, 150, 150, 255), read(gpu, texture, 1, 2, 2), 1, "level 1")
            assertNear(listOf(90, 90, 90, 255), read(gpu, texture, 2, 1, 1), 1, "level 2")
            texture.destroy()
        }
    }

    @Test
    fun colourIsAveragedAsLight() = runTest(timeout = 60.seconds) {
        val gpu = gpuOrSkip("colourIsAveragedAsLight") ?: return@runTest
        gpu.use {
            // Black and white, half and half: half the light is 188 encoded, not 128.
            val pixels = listOf(0, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 0, 0, 0, 255)
            val texture = texture(gpu, GPUTextureFormat.rgba8unormSrgb, 2, pixels)
            Mipmaps.create(gpu).generate(texture, GPUTextureFormat.rgba8unormSrgb, 2)
            assertNear(listOf(188, 188, 188, 255), read(gpu, texture, 1, 1, 1), 2, "level 1 of an sRGB texture")
            texture.destroy()
        }
    }

    @Test
    fun anImageArrivesAsItsBytes() = runTest(timeout = 60.seconds) {
        val gpu = gpuOrSkip("anImageArrivesAsItsBytes") ?: return@runTest
        gpu.use {
            val mipmaps = Mipmaps.create(gpu)
            val expected = listOf(200, 100, 50, 255, 10, 220, 30, 255, 40, 60, 240, 255, 255, 255, 255, 128)
            // As colour (sRGB) or as data, the stored bytes are the file's: no colour management, alpha not multiplied in.
            for (colour in listOf(true, false)) {
                val texture = uploadImage(gpu, mipmaps, TINY_PNG, colour, "test image")
                assertEquals(2, texture.mipLevelCount, "a 2 × 2 image has two levels")
                assertNear(expected, read(gpu, texture, 0, 2, 2), 1, if (colour) "as colour" else "as data")
                texture.destroy()
            }
        }
    }

    @Test
    fun aModelBecomesBuffersAndTextures() = runTest(timeout = 60.seconds) {
        val gpu = gpuOrSkip("aModelBecomesBuffersAndTextures") ?: return@runTest
        gpu.use {
            val image = Model.TextureRef(0, Model.Sampler.DEFAULT, 0)
            val material = Model.Material(
                name = "leaf", baseColorFactor = floatArrayOf(1f, 1f, 1f, 1f), baseColorTexture = image, metallicFactor = 0f, roughnessFactor = 1f,
                metallicRoughnessTexture = null, normalTexture = image, normalScale = 1f, occlusionTexture = null, occlusionStrength = 1f,
                emissiveFactor = Vec3.ZERO, emissiveTexture = null, alphaMode = Model.AlphaMode.Opaque, alphaCutoff = 0.5f, doubleSided = true,
            )
            val triangle = Model.Primitive(
                positions = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f),
                normals = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
                tangents = null,
                texCoords = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f),
                indices = intArrayOf(0, 1, 2),
                material = 0,
                bounds = Bounds(Vec3.ZERO, Vec3(1f, 1f, 0f)),
            )
            val leaves = Model.Mesh("leaf", listOf(triangle), listOf(Mat4.IDENTITY, Mat4.translation(Vec3.X)))
            val model = Model(listOf(leaves), listOf(material), listOf(TINY_PNG), Bounds(Vec3.ZERO, Vec3(2f, 1f, 0f)))
            GpuModel.upload(gpu, model, Mipmaps.create(gpu), "test").use { uploaded ->
                val mesh = uploaded.meshes.single()
                assertEquals(2, mesh.instanceCount)
                assertEquals(2 * 16 * 4.0, mesh.instances.size, "a mat4x4f per instance")
                val primitive = mesh.primitives.single()
                assertEquals(3, primitive.count)
                assertEquals(9 * 4.0, primitive.positions.size)
                assertEquals(null, primitive.tangents, "none in the model, none on the GPU")
                assertEquals(3 * 4.0, primitive.indices!!.size)
                // The same image as colour and as data: two uploads, both there.
                uploaded.texture(image, colour = true)
                uploaded.texture(image, colour = false)
                uploaded.sampler(image)
            }
        }
    }

    private companion object {
        /** A 2 × 2 RGBA PNG: (200, 100, 50), (10, 220, 30), (40, 60, 240), and white at half alpha. */
        val TINY_PNG = Model.Image(
            "89504e470d0a1a0a0000000d494844520000000200000002080600000072b60d24000000194944415478da63389162f49feb8edc7f060d9b0fff81a0010051820a31c8c781910000000049454e44ae426082"
                .chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
            "image/png",
            "tiny",
        )
    }
}
