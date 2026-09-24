// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.records

import com.cdodi.webgpu.runtime.createJsObject
import kotlin.Suppress
import kotlin.js.JsAny

public fun GPUThingRegistry(things: JsAny? = null): GPUThingRegistry = createJsObject {
  things?.let { this.things = it }
}
