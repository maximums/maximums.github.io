// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.dictionary

import com.cdodi.webgpu.runtime.createJsObject
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.js.toJsString

public inline val GPUKind.Companion.plain: GPUKind
  get() = "plain".toJsString().unsafeCast()

public inline val GPUKind.Companion.fancy: GPUKind
  get() = "fancy".toJsString().unsafeCast()

public fun GPUObjectDescriptorBase(label: String? = null): GPUObjectDescriptorBase = createJsObject {
  label?.let { this.label = it }
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
