// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.overriding_operations

import kotlin.Double
import kotlin.Suppress
import kotlin.js.JsAny

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#audioscheduledsourcenode).
 */
public external interface AudioScheduledSourceNode : JsAny {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-audioscheduledsourcenode-start).
   */
  public fun start(`when`: Double)

  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-audioscheduledsourcenode-start).
   */
  public fun start()
}

/**
 * See the [specification](https://gpuweb.github.io/gpuweb/#audiobuffersourcenode).
 */
public external interface AudioBufferSourceNode : AudioScheduledSourceNode {
  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-audiobuffersourcenode-start).
   */
  public fun start(
    `when`: Double,
    offset: Double,
    duration: Double,
  )

  /**
   * See the [specification](https://gpuweb.github.io/gpuweb/#dom-audiobuffersourcenode-start).
   */
  public fun start(`when`: Double, offset: Double)
}
