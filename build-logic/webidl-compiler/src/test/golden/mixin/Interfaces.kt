// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.mixin

import kotlin.String
import kotlin.Suppress
import kotlin.js.JsAny
import kotlin.js.unsafeCast
import org.w3c.dom.Navigator

public external interface GPUThing : JsAny {
  public var label: String
}

public external interface NavigatorThing : JsAny {
  public val thing: GPUThing?
}

public val Navigator.thing: GPUThing?
  get() = unsafeCast<NavigatorThing>().thing
