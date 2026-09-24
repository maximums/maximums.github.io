// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.union_mixed

import kotlin.Double
import kotlin.Suppress

public external interface GPURenderPassEncoder : JsAny {
  public fun setBlendConstant(color: kotlin.js.JsAny)
}

public external interface GPUColorDict : kotlin.js.JsAny {
  public var r: Double

  public var g: Double

  public var b: Double

  public var a: Double
}

public external interface GPURenderPassColorAttachment : kotlin.js.JsAny {
  public var clearValue: kotlin.js.JsAny?
}
