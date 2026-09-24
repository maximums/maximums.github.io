// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.dictionary

import com.cdodi.webgpu.runtime.createJsObject
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.js.JsAny

public external interface GPUObjectDescriptorBase : JsAny {
  public var label: String?
}

public fun GPUObjectDescriptorBase(label: String? = null): GPUObjectDescriptorBase = createJsObject {
  label?.let { this.label = it }
}

public external interface GPUThingDescriptor : JsAny {
  public var size: Int

  public var mappedAtCreation: Boolean?

  public var kind: GPUKind?

  public var label: String?
}

public fun GPUThingDescriptor(
  size: Int,
  mappedAtCreation: Boolean? = null,
  kind: GPUKind? = null,
  label: String? = null,
): GPUThingDescriptor = createJsObject {
  this.size = size
  mappedAtCreation?.let { this.mappedAtCreation = it }
  kind?.let { this.kind = it }
  label?.let { this.label = it }
}
