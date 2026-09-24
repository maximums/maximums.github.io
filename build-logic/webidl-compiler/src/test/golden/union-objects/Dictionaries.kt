// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.union_objects

import com.cdodi.webgpu.runtime.createJsObject
import kotlin.Suppress
import kotlin.js.JsAny

public external interface GPUBufferBinding : JsAny {
  public var buffer: GPUBuffer
}

public fun GPUBufferBinding(buffer: GPUBuffer): GPUBufferBinding = createJsObject {
  this.buffer = buffer
}

public external interface GPUBindGroupEntry : JsAny {
  public var resource: GPUSamplerOrGPUTextureViewOrGPUBufferBinding
}

public fun GPUBindGroupEntry(resource: GPUSamplerOrGPUTextureViewOrGPUBufferBinding): GPUBindGroupEntry = createJsObject {
  this.resource = resource
}

public external interface GPUPipelineDescriptorBase : JsAny {
  public var layout: GPUPipelineLayoutOrGPUAutoLayoutMode
}

public fun GPUPipelineDescriptorBase(layout: GPUPipelineLayoutOrGPUAutoLayoutMode): GPUPipelineDescriptorBase = createJsObject {
  this.layout = layout
}
