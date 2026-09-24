// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.union_mixed

import com.cdodi.webgpu.runtime.createJsObject
import kotlin.Double
import kotlin.Suppress
import kotlin.js.JsAny

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dictdef-gpucolordict).
 */
public external interface GPUColorDict : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gpucolordict-r).
   */
  public var r: Double

  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gpucolordict-g).
   */
  public var g: Double

  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gpucolordict-b).
   */
  public var b: Double

  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gpucolordict-a).
   */
  public var a: Double
}

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dictdef-gpucolordict).
 */
public fun GPUColorDict(
  r: Double,
  g: Double,
  b: Double,
  a: Double,
): GPUColorDict = createJsObject {
  this.r = r
  this.g = g
  this.b = b
  this.a = a
}

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dictdef-gpurenderpasscolorattachment).
 */
public external interface GPURenderPassColorAttachment : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gpurenderpasscolorattachment-clearvalue).
   */
  public var clearValue: JsAny?
}

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dictdef-gpurenderpasscolorattachment).
 */
public fun GPURenderPassColorAttachment(clearValue: JsAny? = null): GPURenderPassColorAttachment = createJsObject {
  clearValue?.let { this.clearValue = it }
}
