// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.extended_attributes

import kotlin.Suppress
import kotlin.js.JsNumber

public external interface GPUThing : JsAny {
  public val self: GPUThing

  public fun draw(count: JsNumber?, level: JsNumber?): kotlin.js.JsAny?

  public fun clone(): GPUThing
}
