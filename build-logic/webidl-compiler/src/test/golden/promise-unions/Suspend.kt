// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.promise_unions

import com.cdodi.webidl.runtime.await
import kotlin.String
import kotlin.Suppress

/**
 * Suspending [AudioContext.setSinkId]: awaits the Promise it returns.
 */
public suspend fun AudioContext.setSinkIdSuspend(sinkId: String) {
  setSinkId(sinkId).await()
}

/**
 * Suspending [AudioContext.setSinkId]: awaits the Promise it returns.
 */
public suspend fun AudioContext.setSinkIdSuspend(sinkId: AudioSinkOptions) {
  setSinkId(sinkId).await()
}
