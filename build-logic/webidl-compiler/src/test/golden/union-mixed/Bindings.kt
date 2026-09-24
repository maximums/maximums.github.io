// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.union_mixed

import kotlin.Double
import kotlin.Suppress
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsNumber

public external interface GPURenderPassEncoder : JsAny {
  public fun setBlendConstant(color: JsArray<JsNumber>)

  public fun setBlendConstant(color: GPUColorDict)
}

public external interface GPUColorDict : JsAny {
  public var r: Double

  public var g: Double

  public var b: Double

  public var a: Double
}

public external interface GPURenderPassColorAttachment : JsAny {
  public var clearValue: JsAny?
}
