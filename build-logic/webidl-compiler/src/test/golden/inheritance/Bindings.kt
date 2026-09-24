// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.inheritance

import kotlin.Suppress
import kotlin.js.JsNumber

public external interface Base : JsAny {
  public val id: JsNumber?
}

public external interface Derived : Base {
  public val extra: JsNumber?
}

public external interface FromDom : JsAny {
  public var onlost: kotlin.js.JsAny?
}
