// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.external_types

import kotlin.Suppress

public external interface GPUBuffer : JsAny

public external interface GPUQueue : JsAny {
  public fun writeBuffer(buffer: GPUBuffer, `data`: kotlin.js.JsAny)

  public fun copyFrom(source: kotlin.js.JsAny)
}
