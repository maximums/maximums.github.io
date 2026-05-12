package com.cdodi

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalWithComputedDefaultOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import org.jetbrains.skia.Data

// Packs Int and Float uniforms into a Skia-compatible byte array.
// Uses little-endian byte order, matching Skia's RuntimeEffect expectations on all Wasm/x86/ARM targets.
fun uniformData(vararg data: Number): Data {
    return ByteArray(size = data.sumOf { Int.SIZE_BYTES }).run {
        data.forEachIndexed { index, number -> number.populate(array = this, offset = index * Int.SIZE_BYTES ) }
        Data.makeFromBytes(bytes = this)
    }
}

private fun Number.populate(array: ByteArray, offset: Int) {
    val number = when (this) {
        is Int -> this
        is Float -> toBits()
        else -> throw IllegalArgumentException("uniformData only supports Int and Float, got ${this::class}")
    }

    // Little-endian: least significant byte first
    repeat(Int.SIZE_BYTES) { byteIdx ->
        array[byteIdx + offset] = (number shr (byteIdx * Byte.SIZE_BITS)).toByte()
    }
}

@Immutable
data class ViewportSize(val width: Dp, val height: Dp)

inline val Number.vw: Dp
    @Composable get() = LocalViewportSize.current.width * (toFloat() / 100f)

inline val Number.vh: Dp
    @Composable get() = LocalViewportSize.current.height * (toFloat() / 100f)

val LocalViewportSize = compositionLocalWithComputedDefaultOf {
    val windowInfo = LocalWindowInfo.currentValue
    val density = LocalDensity.currentValue

    with(density) {
        ViewportSize(
            width = windowInfo.containerSize.width.toDp(),
            height = windowInfo.containerSize.height.toDp()
        )
    }
}

val LocalIsSmallWindow = compositionLocalWithComputedDefaultOf {
    val windowInfo = LocalWindowInfo.currentValue
    val density = LocalDensity.currentValue

    with(density) {
        val width = windowInfo.containerSize.width.toDp()
        val height = windowInfo.containerSize.height.toDp()

        width < 800.dp || height < 800.dp
    }
}

private const val ONE_SECOND_NANOS = 1_000_000_000f

@Composable
fun rememberAnimatedTime(key: Any? = Unit): State<Float> {
    val time = remember { mutableStateOf(0f) }
    LaunchedEffect(key) {
        val startTime = withFrameNanos { it }
        while (isActive) {
            withInfiniteAnimationFrameNanos { frameTimeNanos ->
                time.value = (frameTimeNanos - startTime) / ONE_SECOND_NANOS
            }
        }
    }
    return time
}