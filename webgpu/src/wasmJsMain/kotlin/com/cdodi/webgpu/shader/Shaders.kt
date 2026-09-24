package com.cdodi.webgpu.shader

import com.cdodi.webgpu.bindings.GPUShaderModule
import com.cdodi.webgpu.bindings.GPUShaderModuleDescriptor
import com.cdodi.webgpu.bindings.getCompilationInfoSuspend
import com.cdodi.webgpu.context.GpuContext

data class ShaderMessage(val type: String, val line: Int, val column: Int, val text: String) {
    override fun toString() = "$type at $line:$column: $text"
}

class ShaderCompilationException(val label: String?, val messages: List<ShaderMessage>) :
    Exception("Shader ${label ?: "(unlabelled)"} failed to compile:\n" + messages.joinToString("\n") { "  $it" })

/**
 * Creates a shader module and checks the compiler's messages. WebGPU itself never fails `createShaderModule`: errors
 * only show up later as an invalid pipeline. This surfaces them right here, with line and column, as a
 * [ShaderCompilationException]. Warnings are passed to [onWarning].
 */
suspend fun GpuContext.compileShader(
    code: String,
    label: String? = null,
    onWarning: (ShaderMessage) -> Unit = { println("WGSL ${label ?: ""} $it") },
): GPUShaderModule {
    val module = device.createShaderModule(GPUShaderModuleDescriptor(code = code, label = label))
    val info = module.getCompilationInfoSuspend()

    val messages = List(info.messages.length) { i ->
        val message = info.messages[i]!!
        ShaderMessage(
            type = message.type.unsafeCast<JsString>().toString(),
            line = message.lineNum.toInt(),
            column = message.linePos.toInt(),
            text = message.message,
        )
    }
    messages.filter { it.type == "warning" }.forEach(onWarning)

    val errors = messages.filter { it.type == "error" }
    if (errors.isNotEmpty()) throw ShaderCompilationException(label, errors)
    return module
}
