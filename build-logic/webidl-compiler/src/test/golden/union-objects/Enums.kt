// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.union_objects

import kotlin.Suppress
import kotlin.js.JsAny
import kotlin.js.JsName
import kotlin.js.toJsString

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#enumdef-gpuautolayoutmode).
 */
@JsName("null")
@Suppress("NESTED_CLASS_IN_EXTERNAL_INTERFACE")
public external interface GPUAutoLayoutMode : JsAny, GPUPipelineLayoutOrGPUAutoLayoutMode {
  public companion object
}

public inline val GPUAutoLayoutMode.Companion.auto: GPUAutoLayoutMode
  get() = "auto".toJsString().unsafeCast()
