// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.external_types

import kotlin.Suppress
import kotlin.js.JsAny
import org.khronos.webgl.BufferDataSource
import org.w3c.dom.ImageBitmap

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#gpubuffer).
 */
public external interface GPUBuffer : JsAny

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#gpuqueue).
 */
public external interface GPUQueue : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gpuqueue-writebuffer).
   */
  public fun writeBuffer(buffer: GPUBuffer, `data`: BufferDataSource)

  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gpuqueue-copyfrom).
   */
  public fun copyFrom(source: ImageBitmap)
}
