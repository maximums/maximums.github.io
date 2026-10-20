package com.cdodi.adapters.gpu

import com.cdodi.adapters.input.InputState
import com.cdodi.core.navigation.BackStack
import com.cdodi.core.navigation.NavState
import com.cdodi.core.navigation.graph.SingletonDestination
import com.cdodi.core.navigation.graph.sceneReady
import com.cdodi.core.navigation.signal.MutableSignals
import com.cdodi.core.navigation.signal.Signal
import com.cdodi.core.time.Clock
import com.cdodi.core.time.DefaultHeartbeat
import com.cdodi.core.time.Frame
import com.cdodi.design.theme.AutumnTheme
import com.cdodi.webgpu.bindings.GPUCommandEncoder
import com.cdodi.webgpu.bindings.GPUTextureFormat
import com.cdodi.webgpu.bindings.GPUTextureView
import com.cdodi.webgpu.context.GpuContext
import com.cdodi.webgpu.context.requestGpuContext
import com.cdodi.webidl.runtime.await
import kotlinx.browser.document
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.w3c.dom.HTMLCanvasElement
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private data object Street : SingletonDestination("street")

private fun resolved(): Promise<JsAny?> = js("Promise.resolve(null)")

/**
 * A scene that finishes preparing when it's told to, even if it's been cancelled meanwhile (as a GPU promise already
 * in flight would), and counts what the host asks of it.
 */
private class GatedScene(override val clock: Clock) : Scene {
    val gate = CompletableDeferred<Unit>()
    var resizes = 0
    var updates = 0

    override suspend fun prepare(gpu: GpuContext, format: GPUTextureFormat) = withContext(NonCancellable) { gate.await() }
    override fun resize(width: Int, height: Int) { resizes++ }
    override fun update(frame: Frame, input: InputState) { updates++ }
    override fun encode(encoder: GPUCommandEncoder, target: GPUTextureView) {}
    override fun close() {}
}

/** The host's lifetime on a real device, with a scene it doesn't own the timing of. Skipped without a WebGPU adapter. */
class SceneHostTest {

    private val heartbeat = DefaultHeartbeat()
    private val signals = MutableSignals()
    private val scene = GatedScene(heartbeat.child("scene"))
    private val ready = Signal.sceneReady(Street)
    private var failure: Throwable? = null
    private var now = 0L

    private suspend fun host(gpu: GpuContext, scope: CoroutineScope) = SceneHost(
        gpu = gpu,
        canvas = GpuCanvas(document.createElement("canvas") as HTMLCanvasElement, gpu),
        heartbeat = heartbeat,
        navigation = MutableStateFlow(NavState.Idle(Street, BackStack(listOf(Street)))),
        signals = signals,
        scenes = SceneRegistry(mapOf(Street to scene)),
        effects = emptyList(),
        input = InputState(),
        scope = scope,
        profiler = null,
        glass = GlassCompositor(GpuWidgets()),
        theme = GpuTheme.create(gpu) { AutumnTheme },
        onFailure = { failure = it },
    )

    /** One frame: the heartbeat ticks, the host draws in the microtask after it, and what it started gets to run. */
    private suspend fun TestScope.frame() {
        now += 16_000_000
        heartbeat.tick(now)
        resolved().await()
        runCurrent()
    }

    private fun withGpu(test: suspend TestScope.(GpuContext) -> Unit) = runTest(timeout = 60.seconds) {
        val gpu = requestGpuContext() ?: return@runTest println("SceneHostTest: no WebGPU adapter in this browser — skipped")
        gpu.use { test(it) }
        assertNull(failure)
    }

    @Test
    fun aSceneReadyOnlyAfterItsHostClosedIsNeitherAnnouncedNorStepped() = withGpu { gpu ->
        val host = host(gpu, backgroundScope)
        frame() // the host asks for the scene, which starts preparing
        host.close() // the device is lost meanwhile
        scene.gate.complete(Unit)
        frame()
        frame()
        assertFalse(signals.isRaised(ready))
        assertEquals(0, scene.resizes)
        assertEquals(0, scene.updates)
    }

    @Test
    fun closingWithdrawsTheReadinessItAnnounced() = withGpu { gpu ->
        val host = host(gpu, backgroundScope)
        scene.gate.complete(Unit)
        frame()
        assertTrue(signals.isRaised(ready))
        frame()
        assertTrue(scene.updates > 0)
        host.close()
        assertFalse(signals.isRaised(ready), "a host started after this one must be waited for again")
    }
}
