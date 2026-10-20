package com.cdodi.adapters.gpu.model

import com.cdodi.gltf.Model
import com.cdodi.webgpu.bindings.GPUCopyExternalImageDestInfo
import com.cdodi.webgpu.bindings.GPUCopyExternalImageSourceInfo
import com.cdodi.webgpu.bindings.GPUExtent3DDict
import com.cdodi.webgpu.bindings.GPUTexture
import com.cdodi.webgpu.bindings.GPUTextureDescriptor
import com.cdodi.webgpu.bindings.GPUTextureFormat
import com.cdodi.webgpu.bindings.GPUTextureUsage
import com.cdodi.webgpu.bindings.rgba8unorm
import com.cdodi.webgpu.bindings.rgba8unormSrgb
import com.cdodi.webgpu.context.GpuContext
import com.cdodi.webidl.runtime.await
import kotlinx.browser.window
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.set
import org.w3c.dom.ImageBitmap
import org.w3c.dom.ImageBitmapOptions
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag

/**
 * [image] (a PNG or JPEG) decoded by the browser and uploaded as a texture with all its mip levels. It's decoded as the
 * bytes say, without colour management or premultiplied alpha. A [colour] texture goes to an sRGB format, so shaders
 * sample light; a data texture (normals, roughness) to a plain one. COPY_SRC is there so a test can read it back.
 */
internal suspend fun uploadImage(gpu: GpuContext, mipmaps: Mipmaps, image: Model.Image, colour: Boolean, label: String): GPUTexture {
    val blob = Blob(listOf<JsAny?>(image.bytes.toUint8Array()).toJsArray(), BlobPropertyBag(type = image.mimeType))
    val bitmap: ImageBitmap = window.createImageBitmap(blob, rawImage()).await()
    try {
        val format = if (colour) GPUTextureFormat.rgba8unormSrgb else GPUTextureFormat.rgba8unorm
        val levels = Mipmaps.levelsFor(bitmap.width, bitmap.height)
        val texture = gpu.device.createTexture(
            GPUTextureDescriptor(
                size = GPUExtent3DDict(width = bitmap.width, height = bitmap.height),
                mipLevelCount = levels,
                format = format,
                usage = Mipmaps.USAGE or GPUTextureUsage.COPY_DST or GPUTextureUsage.COPY_SRC,
                label = label,
            )
        )
        gpu.queue.copyExternalImageToTexture(
            GPUCopyExternalImageSourceInfo(source = bitmap),
            GPUCopyExternalImageDestInfo(texture = texture, premultipliedAlpha = false),
            GPUExtent3DDict(width = bitmap.width, height = bitmap.height),
        )
        mipmaps.generate(texture, format, levels)
        return texture
    } finally {
        bitmap.close()
    }
}

/** Kotlin arrays live in the Wasm heap: the bytes go to JavaScript one by one (fine for a few hundred kB, once). */
private fun ByteArray.toUint8Array(): Uint8Array {
    val out = Uint8Array(size)
    for (i in indices) out[i] = this[i]
    return out
}

/** Texture data as it is: no colour-space conversion, no premultiplied alpha. (kotlinx-browser's factory would add an outdated orientation value.) */
private fun rawImage(): ImageBitmapOptions = js("({ premultiplyAlpha: 'none', colorSpaceConversion: 'none' })")
