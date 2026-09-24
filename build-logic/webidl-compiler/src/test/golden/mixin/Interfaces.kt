// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.mixin

import kotlin.String
import kotlin.Suppress
import kotlin.js.JsAny
import kotlin.js.unsafeCast
import org.w3c.dom.Navigator

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#gputhing).
 */
public external interface GPUThing : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-labelled-label).
   */
  public var label: String
}

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#navigatorthing).
 */
public external interface NavigatorThing : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-navigatorthing-thing).
   * Always returns the same object (`[SameObject]`).
   */
  public val thing: GPUThing?
}

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dom-navigatorthing-thing).
 * Always returns the same object (`[SameObject]`).
 */
public val Navigator.thing: GPUThing?
  get() = unsafeCast<NavigatorThing>().thing
