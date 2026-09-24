// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.dictionary

import kotlin.Suppress
import kotlin.js.JsAny
import kotlin.js.JsBoolean
import kotlin.js.JsNumber
import kotlin.js.JsString

public sealed external interface GPUKind : JsAny

public external interface GPUObjectDescriptorBase : JsAny {
  public var label: JsString?
}

public external interface GPUThingDescriptor : JsAny {
  public var size: JsNumber

  public var mappedAtCreation: JsBoolean?

  public var kind: GPUKind?

  public var label: JsString?
}
