// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.constructors

import kotlin.String
import kotlin.Suppress
import kotlin.js.JsAny

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#gpuerror).
 */
public external interface GPUError : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gpuerror-message).
   */
  public val message: String
}

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#gpuvalidationerror).
 * Exposed to: Window, Worker.
 */
public abstract external class GPUValidationError : GPUError {
  public constructor(message: String)
}
