// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.sequences

import kotlin.Suppress
import kotlin.js.JsArray
import kotlin.js.JsNumber
import kotlin.js.JsString

public external interface GPUThing : JsAny {
  public val tags: JsArray<JsString>
}

public external interface GPUThingList : kotlin.js.JsAny {
  public var things: JsArray<GPUThing>

  public var counts: JsArray<JsNumber>?
}
