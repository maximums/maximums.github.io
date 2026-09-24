// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.enums

import kotlin.Suppress
import kotlin.js.JsAny
import kotlin.js.JsName
import kotlin.js.toJsString

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#enumdef-gpuprimitivetopology).
 */
@JsName("null")
@Suppress("NESTED_CLASS_IN_EXTERNAL_INTERFACE")
public external interface GPUPrimitiveTopology : JsAny {
  public companion object
}

public inline val GPUPrimitiveTopology.Companion.pointList: GPUPrimitiveTopology
  get() = "point-list".toJsString().unsafeCast()

public inline val GPUPrimitiveTopology.Companion.triangleList: GPUPrimitiveTopology
  get() = "triangle-list".toJsString().unsafeCast()

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#enumdef-gputextureviewdimension).
 */
@JsName("null")
@Suppress("NESTED_CLASS_IN_EXTERNAL_INTERFACE")
public external interface GPUTextureViewDimension : JsAny {
  public companion object
}

public inline val GPUTextureViewDimension.Companion.`1d`: GPUTextureViewDimension
  get() = "1d".toJsString().unsafeCast()

public inline val GPUTextureViewDimension.Companion.`2dArray`: GPUTextureViewDimension
  get() = "2d-array".toJsString().unsafeCast()
