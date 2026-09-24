// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.typedefs

import kotlin.Double
import kotlin.Int
import kotlin.Suppress
import kotlin.js.JsAny

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#gpubuffer).
 */
public external interface GPUBuffer : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gpubuffer-size).
   * Throws a TypeError if the value is not finite or out of range (`[EnforceRange]`).
   */
  public val size: Double

  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gpubuffer-resize).
   * @param count Throws a TypeError if the value is not finite or out of range (`[EnforceRange]`).
   * @param limit Throws a TypeError if the value is not finite or out of range (`[EnforceRange]`).
   */
  public fun resize(count: Int, limit: Double?)
}
