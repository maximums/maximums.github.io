// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.promises

import com.cdodi.webgpu.runtime.createJsObject
import kotlin.Boolean
import kotlin.Suppress
import kotlin.js.JsAny

public external interface GPURequestAdapterOptions : JsAny {
  public var forceFallbackAdapter: Boolean?
}

public fun GPURequestAdapterOptions(forceFallbackAdapter: Boolean? = null): GPURequestAdapterOptions = createJsObject {
  forceFallbackAdapter?.let { this.forceFallbackAdapter = it }
}
