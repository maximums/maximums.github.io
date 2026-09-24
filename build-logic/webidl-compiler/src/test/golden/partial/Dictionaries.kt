// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.partial

import com.cdodi.webgpu.runtime.createJsObject
import kotlin.Boolean
import kotlin.Suppress
import kotlin.js.JsAny

public external interface GPUThingOptions : JsAny {
  public var first: Boolean?

  public var second: Boolean?
}

public fun GPUThingOptions(first: Boolean? = null, second: Boolean? = null): GPUThingOptions = createJsObject {
  first?.let { this.first = it }
  second?.let { this.second = it }
}
