// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.basic_interface

import kotlin.Boolean
import kotlin.Double
import kotlin.String
import kotlin.Suppress
import kotlin.js.JsAny

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#gputhing).
 */
public external interface GPUThing : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gputhing-name).
   */
  public val name: String

  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gputhing-level).
   */
  public var level: Double

  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gputhing-reset).
   */
  public fun reset()

  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gputhing-compare).
   */
  public fun compare(other: GPUThing): Boolean
}
