// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.nested_types

import kotlin.Suppress
import kotlin.js.JsAny

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#gpusampler).
 */
public external interface GPUSampler : GPUSamplerOrGPUTextureViewOrGPUBuffer

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#gputextureview).
 */
public external interface GPUTextureView : GPUTextureViewOrGPUBuffer,
    GPUSamplerOrGPUTextureViewOrGPUBuffer

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#gpubuffer).
 */
public external interface GPUBuffer : GPUTextureViewOrGPUBuffer,
    GPUSamplerOrGPUTextureViewOrGPUBuffer

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#gpubinder).
 */
public external interface GPUBinder : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gpubinder-bind).
   */
  public fun bind(resource: GPUSamplerOrGPUTextureViewOrGPUBuffer)
}

/**
 * A union: accepts [GPUTextureView], [GPUBuffer]. Each of them extends this marker.
 */
public external interface GPUTextureViewOrGPUBuffer : JsAny

/**
 * A union: accepts [GPUSampler], [GPUTextureView], [GPUBuffer]. Each of them extends this marker.
 */
public external interface GPUSamplerOrGPUTextureViewOrGPUBuffer : JsAny
