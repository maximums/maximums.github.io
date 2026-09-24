// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.sequences

import com.cdodi.webgpu.runtime.createJsObject
import kotlin.Int
import kotlin.Suppress
import kotlin.collections.List
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsNumber
import kotlin.js.toJsArray
import kotlin.js.toJsNumber

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dictdef-gputhinglist).
 */
public external interface GPUThingList : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gputhinglist-things).
   */
  public var things: JsArray<GPUThing>

  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gputhinglist-counts).
   */
  public var counts: JsArray<JsNumber>?
}

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dictdef-gputhinglist).
 */
public fun GPUThingList(things: List<GPUThing>, counts: List<Int>? = null): GPUThingList = createJsObject {
  this.things = things.toJsArray()
  counts?.let { this.counts = it.map { it.toJsNumber() }.toJsArray() }
}
