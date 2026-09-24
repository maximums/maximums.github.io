// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.records

import com.cdodi.webgpu.runtime.createJsObject
import kotlin.Suppress
import kotlin.js.JsAny

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dictdef-gputhingregistry).
 */
public external interface GPUThingRegistry : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gputhingregistry-things).
   */
  public var things: JsAny?
}

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dictdef-gputhingregistry).
 */
public fun GPUThingRegistry(things: JsAny? = null): GPUThingRegistry = createJsObject {
  things?.let { this.things = it }
}
