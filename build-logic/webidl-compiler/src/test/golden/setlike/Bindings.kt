// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.setlike

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.js.JsAny

public external interface GPUSupportedFeatures : JsAny {
  public val size: Int

  public fun has(`value`: String): Boolean
}

public external interface GPUThingSet : JsAny {
  public val size: Int

  public fun has(`value`: String): Boolean

  public fun add(`value`: String): GPUThingSet

  public fun delete(`value`: String): Boolean

  public fun clear()
}
