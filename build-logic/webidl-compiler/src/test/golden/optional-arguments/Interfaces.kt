// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.optional_arguments

import kotlin.Double
import kotlin.Suppress
import kotlin.js.JsAny
import org.khronos.webgl.ArrayBuffer

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#gpubuffer).
 */
public external interface GPUBuffer : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gpubuffer-getmappedrange).
   * @param offset Throws a TypeError if the value is not finite or out of range (`[EnforceRange]`).
   * @param size Throws a TypeError if the value is not finite or out of range (`[EnforceRange]`).
   */
  public fun getMappedRange(offset: Double = definedExternally, size: Double = definedExternally): ArrayBuffer
}
