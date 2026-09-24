// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.basic_interface

import kotlin.Suppress
import kotlin.js.JsBoolean
import kotlin.js.JsNumber
import kotlin.js.JsString

public external interface GPUThing : JsAny {
  public val name: JsString?

  public var level: JsNumber?

  public fun reset(): kotlin.js.JsAny?

  public fun compare(other: GPUThing): JsBoolean?
}
