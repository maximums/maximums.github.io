// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.setlike

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.js.JsAny

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#gpusupportedfeatures).
 */
public external interface GPUSupportedFeatures : JsAny {
  public val size: Int

  public fun has(`value`: String): Boolean
}

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#gputhingset).
 */
public external interface GPUThingSet : JsAny {
  public val size: Int

  public fun has(`value`: String): Boolean

  public fun add(`value`: String): GPUThingSet

  public fun delete(`value`: String): Boolean

  public fun clear()
}
