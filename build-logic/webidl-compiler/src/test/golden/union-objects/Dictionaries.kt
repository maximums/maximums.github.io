// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.union_objects

import com.cdodi.webgpu.runtime.createJsObject
import kotlin.Suppress
import kotlin.js.JsAny

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dictdef-gpubufferbinding).
 */
public external interface GPUBufferBinding : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gpubufferbinding-buffer).
   */
  public var buffer: GPUBuffer
}

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dictdef-gpubufferbinding).
 */
public fun GPUBufferBinding(buffer: GPUBuffer): GPUBufferBinding = createJsObject {
  this.buffer = buffer
}

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dictdef-gpubindgroupentry).
 */
public external interface GPUBindGroupEntry : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gpubindgroupentry-resource).
   */
  public var resource: GPUSamplerOrGPUTextureViewOrGPUBufferBinding
}

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dictdef-gpubindgroupentry).
 */
public fun GPUBindGroupEntry(resource: GPUSamplerOrGPUTextureViewOrGPUBufferBinding): GPUBindGroupEntry = createJsObject {
  this.resource = resource
}

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dictdef-gpupipelinedescriptorbase).
 */
public external interface GPUPipelineDescriptorBase : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gpupipelinedescriptorbase-layout).
   */
  public var layout: GPUPipelineLayoutOrGPUAutoLayoutMode
}

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dictdef-gpupipelinedescriptorbase).
 */
public fun GPUPipelineDescriptorBase(layout: GPUPipelineLayoutOrGPUAutoLayoutMode): GPUPipelineDescriptorBase = createJsObject {
  this.layout = layout
}
