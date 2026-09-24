// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.extended_attributes

import kotlin.Int
import kotlin.Suppress
import kotlin.js.JsAny

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#gputhing).
 * Exposed to: Window, DedicatedWorker.
 * Only available in secure contexts (HTTPS).
 */
public external interface GPUThing : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gputhing-self).
   * Always returns the same object (`[SameObject]`).
   */
  public val self: GPUThing

  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gputhing-draw).
   * @param count Throws a TypeError if the value is not finite or out of range (`[EnforceRange]`).
   * @param level Out-of-range values are clamped (`[Clamp]`).
   */
  public fun draw(count: Int, level: Int)

  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gputhing-clone).
   * Returns a new object every time (`[NewObject]`).
   */
  public fun clone(): GPUThing
}
