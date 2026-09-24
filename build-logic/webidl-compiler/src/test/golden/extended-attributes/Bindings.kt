// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.extended_attributes

import kotlin.Int
import kotlin.Suppress

public external interface GPUThing : JsAny {
  public val self: GPUThing

  public fun draw(count: Int, level: Int)

  public fun clone(): GPUThing
}
