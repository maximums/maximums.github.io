// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.promises

import kotlin.Suppress
import kotlin.js.JsAny
import kotlin.js.Promise

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#gpuadapter).
 */
public external interface GPUAdapter : JsAny

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#gpu).
 */
public external interface GPU : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gpu-requestadapter).
   */
  public fun requestAdapter(options: GPURequestAdapterOptions = definedExternally): Promise<GPUAdapter?>

  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gpu-onsubmittedworkdone).
   */
  public fun onSubmittedWorkDone(): Promise<JsAny?>
}
