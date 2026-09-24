// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.mixin

import kotlin.Suppress
import kotlin.js.unsafeCast
import org.w3c.dom.Navigator

public val Navigator.thing: GPUThing?
  get() = unsafeCast<NavigatorThing>().thing
