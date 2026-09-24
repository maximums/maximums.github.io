// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.union_mixed

import com.cdodi.webgpu.runtime.createJsObject
import kotlin.Double
import kotlin.Suppress
import kotlin.js.JsAny

public fun GPUColorDict(
  r: Double,
  g: Double,
  b: Double,
  a: Double,
): GPUColorDict = createJsObject {
  this.r = r
  this.g = g
  this.b = b
  this.a = a
}

public fun GPURenderPassColorAttachment(clearValue: JsAny? = null): GPURenderPassColorAttachment = createJsObject {
  clearValue?.let { this.clearValue = it }
}
