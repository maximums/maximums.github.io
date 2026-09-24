package com.cdodi.webidl.gradle

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property

/**
 * ```
 * webIdl {
 *     idlFiles.from("idl/webgpu.idl")
 *     packageName = "com.cdodi.webgpu.bindings"
 *     runtimePackage = "com.cdodi.webgpu.runtime"
 * }
 * ```
 */
abstract class WebIdlExtension {

    /** Committed `.idl` files, read on every build. Only `updateWebIdl` rewrites them. */
    abstract val idlFiles: ConfigurableFileCollection

    /** Package of the generated declarations. */
    abstract val packageName: Property<String>

    /** Package holding the runtime helpers the generated code calls (`createJsObject`, `await`, ...). */
    abstract val runtimePackage: Property<String>

    abstract val apiFileName: Property<String>

    abstract val factoriesFileName: Property<String>
}
