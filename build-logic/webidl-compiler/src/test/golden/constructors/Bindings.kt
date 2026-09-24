// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.constructors

import kotlin.Suppress
import kotlin.js.JsString

public external interface GPUError : JsAny {
  public val message: JsString?
}

public abstract external class GPUValidationError : GPUError {
  public constructor(message: JsString?)
}
