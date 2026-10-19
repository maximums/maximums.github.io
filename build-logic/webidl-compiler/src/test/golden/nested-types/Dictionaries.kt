// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.nested_types

import com.cdodi.webidl.runtime.createJsObject
import kotlin.Int
import kotlin.Suppress
import kotlin.collections.List
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsNumber
import kotlin.js.toJsArray
import kotlin.js.toJsNumber

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dictdef-gpubinding).
 */
public external interface GPUBinding : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gpubinding-resource).
   */
  public var resource: GPUSamplerOrGPUTextureViewOrGPUBuffer

  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gpubinding-viatypedef).
   */
  public var viaTypedef: GPUSamplerOrGPUTextureViewOrGPUBuffer

  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gpubinding-grid).
   */
  public var grid: JsArray<JsArray<JsNumber>>
}

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dictdef-gpubinding).
 */
public fun GPUBinding(
  resource: GPUSamplerOrGPUTextureViewOrGPUBuffer,
  viaTypedef: GPUSamplerOrGPUTextureViewOrGPUBuffer,
  grid: List<List<Int>>,
): GPUBinding = createJsObject {
  this.resource = resource
  this.viaTypedef = viaTypedef
  this.grid = grid.map { it.map { it.toJsNumber() }.toJsArray() }.toJsArray()
}
