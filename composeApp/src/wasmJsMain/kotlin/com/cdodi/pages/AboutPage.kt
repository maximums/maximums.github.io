package com.cdodi.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.cdodi.buses.LocalTimeBus
import kotlinx.coroutines.flow.scan
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

// language=agsl
const val MY_TRY = """
    uniform float2 resolution;
    uniform float time;
    uniform float flag;
    uniform shader composable;
    
    half4 main(float2 pixelAgslCoord) {
        float2 glslPixelCoord = float2(pixelAgslCoord.x, resolution.y - pixelAgslCoord.y);      // from top-left to bottom-left, flip Y-axis
        float2 uv = glslPixelCoord / resolution;                                                // normalize coordinates
        uv -= 0.5;                                                                              // move 0,0 to center of the screen
        uv.x *= resolution.x / resolution.y;                                                    // aspect ratio
        
        float3 cameraPos = float3(0.0, 0.0, flag * sin(time) / 2);
        float3 lightDir = normalize(float3(sin(time), 1.0, -cos(time)));
        float focalLength = 1.0;
        float3 screenPixelPos = float3(uv.x, uv.y, focalLength);
        float3 rayDirection = normalize(screenPixelPos - cameraPos);
        float3 sphereCenter = float3(0.0, 0.0, 5.0);
        float sphereRadius = 1.0;
        float3 V  = cameraPos - sphereCenter;
        float b = 2.0 * dot(rayDirection, V);
        float c = dot(V, V) - sphereRadius * sphereRadius;
        float discriminant = b * b - 4.0 * c;
        float hit = step(0.0, discriminant);
        float t = (-b - sqrt(max(discriminant, 0.0))) / 2.0;
        float3 hitPoint = cameraPos + t * rayDirection;
        float3 normal = normalize(hitPoint - sphereCenter);
        float brightness = max(dot(normal, lightDir), 0.0);
        brightness = brightness * 0.9 + 0.1;
        half4 composeColor = composable.eval(pixelAgslCoord);
        float color = brightness * hit;
        composeColor.rgb += color;
        composeColor.a = step(0.09, color);
        
        return composeColor;
    }
"""

@Composable
fun AboutPage() {
    val effect = remember { RuntimeEffect.makeForShader(MY_TRY) }
    val heartBeat = LocalTimeBus.current
    val accumulatedTimeFlow = remember(effect, heartBeat) {
        heartBeat.ticks.scan(0f) { accumulator, tick ->  (accumulator + tick) % 10000f }
    }
    val time by accumulatedTimeFlow.collectAsState(0f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val builder = RuntimeShaderBuilder(effect).apply {
                    uniform("resolution", size.width, size.height)
                    uniform("time", time)
                    uniform("flag", 0.0f)
                }

                val skiaFilter = ImageFilter.makeRuntimeShader(
                    builder,
                    shaderNames = arrayOf(),
                    inputs = arrayOf()
                )

                renderEffect = skiaFilter.asComposeRenderEffect()
            }
    )
}