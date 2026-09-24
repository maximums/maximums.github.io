// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.partial

import com.cdodi.webgpu.runtime.createJsObject
import kotlin.Boolean
import kotlin.Suppress
import kotlin.js.JsAny

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dictdef-gputhingoptions).
 */
public external interface GPUThingOptions : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gputhingoptions-first).
   */
  public var first: Boolean?

  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gputhingoptions-second).
   */
  public var second: Boolean?
}

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dictdef-gputhingoptions).
 */
public fun GPUThingOptions(first: Boolean? = null, second: Boolean? = null): GPUThingOptions = createJsObject {
  first?.let { this.first = it }
  second?.let { this.second = it }
}
