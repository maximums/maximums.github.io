// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.promises

import kotlin.Suppress
import kotlin.js.JsBoolean
import kotlin.js.Promise

public external interface GPUAdapter : JsAny

public external interface GPU : JsAny {
  public fun requestAdapter(options: GPURequestAdapterOptions = definedExternally): Promise<GPUAdapter>

  public fun onSubmittedWorkDone(): Promise<kotlin.js.JsAny?>
}

public external interface GPURequestAdapterOptions : kotlin.js.JsAny {
  public var forceFallbackAdapter: JsBoolean?
}
