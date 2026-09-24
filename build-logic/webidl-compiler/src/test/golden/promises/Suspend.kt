// Current file is generated, please don't modify it manually because your changes will be lost.
@file:Suppress("Unused", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "ObjectPropertyName", "RemoveRedundantQualifierName")

package fixtures.promises

import com.cdodi.webgpu.runtime.await
import kotlin.Suppress

public suspend fun GPU.requestAdapterSuspend(options: GPURequestAdapterOptions): GPUAdapter? = requestAdapter(options).await()

public suspend fun GPU.requestAdapterSuspend(): GPUAdapter? = requestAdapter().await()

public suspend fun GPU.onSubmittedWorkDoneSuspend() {
  onSubmittedWorkDone().await()
}
