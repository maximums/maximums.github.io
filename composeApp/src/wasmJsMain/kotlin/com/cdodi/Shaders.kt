@file:Suppress("unused")

package com.cdodi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import blog.composeapp.generated.resources.Res
import com.cdodi.components.RuntimeShader

// language=agsl
private const val FALLBACK_SHADER = """
    uniform int width;
    uniform int height;
    uniform float iTime;

    half4 main(vec2 fragCoord) {
        return half4(0.05, 0.05, 0.15, 1.0);
    }
"""

// TODO need to test it first
// language=agsl
@Composable
fun rememberShader(name: String): State<RuntimeShader> = produceState(RuntimeShader(FALLBACK_SHADER)) {
    value = RuntimeShader(Res.readBytes("files/$name.sksl").decodeToString())
}
