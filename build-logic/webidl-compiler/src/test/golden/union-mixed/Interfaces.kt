// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.union_mixed

import kotlin.Suppress
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsNumber

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#gpurenderpassencoder).
 */
public external interface GPURenderPassEncoder : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gpurenderpassencoder-setblendconstant).
   */
  public fun setBlendConstant(color: JsArray<JsNumber>)

  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gpurenderpassencoder-setblendconstant).
   */
  public fun setBlendConstant(color: GPUColorDict)
}
