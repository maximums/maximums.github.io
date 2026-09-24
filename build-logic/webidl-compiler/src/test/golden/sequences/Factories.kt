// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.sequences

import com.cdodi.webgpu.runtime.createJsObject
import com.cdodi.webgpu.runtime.toJsArray
import kotlin.Int
import kotlin.Suppress
import kotlin.collections.List
import kotlin.js.toJsNumber

public fun GPUThingList(things: List<GPUThing>, counts: List<Int>? = null): GPUThingList = createJsObject {
  this.things = things.toJsArray()
  counts?.let { this.counts = it.map { it.toJsNumber() }.toJsArray() }
}
