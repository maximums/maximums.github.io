// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.partial

import com.cdodi.webgpu.runtime.createJsObject
import kotlin.Boolean
import kotlin.Suppress

public fun GPUThingOptions(first: Boolean? = null, second: Boolean? = null): GPUThingOptions = createJsObject {
  first?.let { this.first = it }
  second?.let { this.second = it }
}
