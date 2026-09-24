// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.promises

import com.cdodi.webgpu.runtime.createJsObject
import kotlin.Boolean
import kotlin.Suppress
import kotlin.js.JsAny

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dictdef-gpurequestadapteroptions).
 */
public external interface GPURequestAdapterOptions : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gpurequestadapteroptions-forcefallbackadapter).
   */
  public var forceFallbackAdapter: Boolean?
}

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dictdef-gpurequestadapteroptions).
 */
public fun GPURequestAdapterOptions(forceFallbackAdapter: Boolean? = null): GPURequestAdapterOptions = createJsObject {
  forceFallbackAdapter?.let { this.forceFallbackAdapter = it }
}
