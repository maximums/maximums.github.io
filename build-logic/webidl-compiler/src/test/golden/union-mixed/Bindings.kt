// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.union_mixed

import kotlin.Suppress
import kotlin.js.JsNumber

public external interface GPURenderPassEncoder : JsAny {
  public fun setBlendConstant(color: kotlin.js.JsAny?): kotlin.js.JsAny?
}

public external interface GPUColorDict : kotlin.js.JsAny {
  public var r: JsNumber

  public var g: JsNumber

  public var b: JsNumber

  public var a: JsNumber
}

public external interface GPURenderPassColorAttachment : kotlin.js.JsAny {
  public var clearValue: kotlin.js.JsAny?
}
