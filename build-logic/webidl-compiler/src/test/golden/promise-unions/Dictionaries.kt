// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.promise_unions

import com.cdodi.webidl.runtime.createJsObject
import kotlin.String
import kotlin.Suppress
import kotlin.js.JsAny

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dictdef-audiosinkoptions).
 */
public external interface AudioSinkOptions : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-audiosinkoptions-type).
   */
  public var type: String
}

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dictdef-audiosinkoptions).
 */
public fun AudioSinkOptions(type: String): AudioSinkOptions = createJsObject {
  this.type = type
}
