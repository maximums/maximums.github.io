// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.union_mixed

import kotlin.Suppress
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsNumber

public external interface GPURenderPassEncoder : JsAny {
  public fun setBlendConstant(color: JsArray<JsNumber>)

  public fun setBlendConstant(color: GPUColorDict)
}
