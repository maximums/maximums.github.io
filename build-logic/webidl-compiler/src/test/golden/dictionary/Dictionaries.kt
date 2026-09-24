// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.dictionary

import com.cdodi.webgpu.runtime.createJsObject
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.js.JsAny

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dictdef-gpuobjectdescriptorbase).
 */
public external interface GPUObjectDescriptorBase : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gpuobjectdescriptorbase-label).
   */
  public var label: String?
}

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dictdef-gpuobjectdescriptorbase).
 */
public fun GPUObjectDescriptorBase(label: String? = null): GPUObjectDescriptorBase = createJsObject {
  label?.let { this.label = it }
}

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dictdef-gputhingdescriptor).
 */
public external interface GPUThingDescriptor : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gputhingdescriptor-size).
   */
  public var size: Int

  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gputhingdescriptor-mappedatcreation).
   */
  public var mappedAtCreation: Boolean?

  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gputhingdescriptor-kind).
   */
  public var kind: GPUKind?

  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-gpuobjectdescriptorbase-label).
   */
  public var label: String?
}

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#dictdef-gputhingdescriptor).
 */
public fun GPUThingDescriptor(
  size: Int,
  mappedAtCreation: Boolean? = null,
  kind: GPUKind? = null,
  label: String? = null,
): GPUThingDescriptor = createJsObject {
  this.size = size
  mappedAtCreation?.let { this.mappedAtCreation = it }
  kind?.let { this.kind = it }
  label?.let { this.label = it }
}
