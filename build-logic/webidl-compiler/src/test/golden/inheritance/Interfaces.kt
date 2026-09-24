// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.inheritance

import kotlin.Int
import kotlin.Suppress
import kotlin.js.JsAny
import org.w3c.dom.events.EventTarget

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#base).
 */
public external interface Base : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-base-id).
   */
  public val id: Int
}

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#derived).
 */
public external interface Derived : Base {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-derived-extra).
   */
  public val extra: Int
}

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#fromdom).
 */
public abstract external class FromDom : EventTarget {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-fromdom-onlost).
   */
  public var onlost: JsAny?
}
