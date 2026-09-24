// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.union_objects

import com.cdodi.webgpu.runtime.createJsObject
import kotlin.Suppress
import kotlin.js.toJsString

public inline val GPUAutoLayoutMode.Companion.auto: GPUAutoLayoutMode
  get() = "auto".toJsString().unsafeCast()

public fun GPUBufferBinding(buffer: GPUBuffer): GPUBufferBinding = createJsObject {
  this.buffer = buffer
}

public fun GPUBindGroupEntry(resource: GPUSamplerOrGPUTextureViewOrGPUBufferBinding): GPUBindGroupEntry = createJsObject {
  this.resource = resource
}

public fun GPUPipelineDescriptorBase(layout: GPUPipelineLayoutOrGPUAutoLayoutMode): GPUPipelineDescriptorBase = createJsObject {
  this.layout = layout
}
