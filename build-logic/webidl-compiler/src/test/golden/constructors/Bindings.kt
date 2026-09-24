// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.constructors

import kotlin.String
import kotlin.Suppress
import kotlin.js.JsAny

public external interface GPUError : JsAny {
  public val message: String
}

public abstract external class GPUValidationError : GPUError {
  public constructor(message: String)
}
