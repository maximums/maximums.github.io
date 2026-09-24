// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.dictionary

import kotlin.Suppress
import kotlin.js.JsAny
import kotlin.js.JsName
import kotlin.js.toJsString

@JsName("null")
@Suppress("NESTED_CLASS_IN_EXTERNAL_INTERFACE")
public external interface GPUKind : JsAny {
  public companion object
}

public inline val GPUKind.Companion.plain: GPUKind
  get() = "plain".toJsString().unsafeCast()

public inline val GPUKind.Companion.fancy: GPUKind
  get() = "fancy".toJsString().unsafeCast()
