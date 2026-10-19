// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.float_sequences

import com.cdodi.webgpu.runtime.createJsObject
import kotlin.Boolean
import kotlin.Float
import kotlin.Suppress
import kotlin.collections.List
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsNumber
import kotlin.js.toJsArray
import kotlin.js.toJsNumber

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dictdef-periodicwaveoptions).
 */
public external interface PeriodicWaveOptions : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-periodicwaveoptions-real).
   */
  public var real: JsArray<JsNumber>?

  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-periodicwaveoptions-imag).
   */
  public var imag: JsArray<JsNumber>?

  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-periodicwaveoptions-disablenormalization).
   */
  public var disableNormalization: Boolean?
}

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dictdef-periodicwaveoptions).
 */
public fun PeriodicWaveOptions(
  real: List<Float>? = null,
  imag: List<Float>? = null,
  disableNormalization: Boolean? = null,
): PeriodicWaveOptions = createJsObject {
  real?.let { this.real = it.map { it.toDouble().toJsNumber() }.toJsArray() }
  imag?.let { this.imag = it.map { it.toDouble().toJsNumber() }.toJsArray() }
  disableNormalization?.let { this.disableNormalization = it }
}
