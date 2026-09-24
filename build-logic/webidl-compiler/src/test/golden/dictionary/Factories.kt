// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.dictionary

import com.cdodi.webgpu.runtime.createJsObject
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.js.toJsBoolean
import kotlin.js.toJsNumber
import kotlin.js.toJsString

public object GPUKindEntries {
  public inline val `plain`: GPUKind
    get() = "plain".toJsString().unsafeCast()

  public inline val `fancy`: GPUKind
    get() = "fancy".toJsString().unsafeCast()
}

public fun GPUObjectDescriptorBase(label: String? = null): GPUObjectDescriptorBase = createJsObject {
  label?.let { this.label = it.toJsString() }
}

public fun GPUThingDescriptor(
  size: Int,
  mappedAtCreation: Boolean? = null,
  kind: GPUKind? = null,
  label: String? = null,
): GPUThingDescriptor = createJsObject {
  this.size = size.toJsNumber()
  mappedAtCreation?.let { this.mappedAtCreation = it.toJsBoolean() }
  kind?.let { this.kind = it }
  label?.let { this.label = it.toJsString() }
}
