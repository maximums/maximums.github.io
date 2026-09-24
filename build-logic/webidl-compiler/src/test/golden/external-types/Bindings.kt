// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.external_types

import kotlin.Suppress
import kotlin.js.JsAny
import org.khronos.webgl.BufferDataSource
import org.w3c.dom.ImageBitmap

public external interface GPUBuffer : JsAny

public external interface GPUQueue : JsAny {
  public fun writeBuffer(buffer: GPUBuffer, `data`: BufferDataSource)

  public fun copyFrom(source: ImageBitmap)
}
