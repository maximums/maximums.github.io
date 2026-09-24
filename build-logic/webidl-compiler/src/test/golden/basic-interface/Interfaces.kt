// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.basic_interface

import kotlin.Boolean
import kotlin.Double
import kotlin.String
import kotlin.Suppress
import kotlin.js.JsAny

public external interface GPUThing : JsAny {
  public val name: String

  public var level: Double

  public fun reset()

  public fun compare(other: GPUThing): Boolean
}
