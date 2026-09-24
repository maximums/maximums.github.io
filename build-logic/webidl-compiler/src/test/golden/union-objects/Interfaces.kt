// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.union_objects

import kotlin.Suppress
import kotlin.js.JsAny

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#gpusampler).
 */
public external interface GPUSampler : GPUSamplerOrGPUTextureViewOrGPUBufferBinding

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#gputextureview).
 */
public external interface GPUTextureView : GPUSamplerOrGPUTextureViewOrGPUBufferBinding

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#gpubuffer).
 */
public external interface GPUBuffer : JsAny

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#gpudevice).
 */
public external interface GPUDevice : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gpudevice-bind).
   */
  public fun bind(resource: GPUSamplerOrGPUTextureViewOrGPUBufferBinding)
}

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#gpupipelinelayout).
 */
public external interface GPUPipelineLayout : GPUPipelineLayoutOrGPUAutoLayoutMode

/**
 * A union: accepts [GPUSampler], [GPUTextureView], [GPUBufferBinding]. Each of them extends this marker.
 */
public external interface GPUSamplerOrGPUTextureViewOrGPUBufferBinding : JsAny

/**
 * A union: accepts [GPUPipelineLayout], [GPUAutoLayoutMode]. Each of them extends this marker.
 */
public external interface GPUPipelineLayoutOrGPUAutoLayoutMode : JsAny
