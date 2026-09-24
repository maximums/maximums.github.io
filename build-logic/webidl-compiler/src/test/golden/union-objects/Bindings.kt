// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.union_objects

import kotlin.Suppress
import kotlin.js.JsAny

public external interface GPUSampler : GPUSamplerOrGPUTextureViewOrGPUBufferBinding

public external interface GPUTextureView : GPUSamplerOrGPUTextureViewOrGPUBufferBinding

public external interface GPUBuffer : JsAny

public external interface GPUDevice : JsAny {
  public fun bind(resource: GPUSamplerOrGPUTextureViewOrGPUBufferBinding)
}

public external interface GPUSamplerOrGPUTextureViewOrGPUBufferBinding : JsAny

public external interface GPUBufferBinding : JsAny {
  public var buffer: GPUBuffer
}

public external interface GPUBindGroupEntry : JsAny {
  public var resource: GPUSamplerOrGPUTextureViewOrGPUBufferBinding
}
