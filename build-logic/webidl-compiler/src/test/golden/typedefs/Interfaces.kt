// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.typedefs

import kotlin.Double
import kotlin.Int
import kotlin.Suppress
import kotlin.js.JsAny

public external interface GPUBuffer : JsAny {
  public val size: Double

  public fun resize(count: Int, limit: Double?)
}
