// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.dictionary

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.js.JsAny

public sealed external interface GPUKind : JsAny

public external interface GPUObjectDescriptorBase : JsAny {
  public var label: String?
}

public external interface GPUThingDescriptor : JsAny {
  public var size: Int

  public var mappedAtCreation: Boolean?

  public var kind: GPUKind?

  public var label: String?
}
