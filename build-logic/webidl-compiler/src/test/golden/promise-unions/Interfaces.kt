// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.promise_unions

import kotlin.String
import kotlin.Suppress
import kotlin.js.JsAny
import kotlin.js.Promise

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#audiocontext).
 */
public external interface AudioContext : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-audiocontext-setsinkid).
   */
  public fun setSinkId(sinkId: String): Promise<JsAny?>

  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-audiocontext-setsinkid).
   */
  public fun setSinkId(sinkId: AudioSinkOptions): Promise<JsAny?>
}
