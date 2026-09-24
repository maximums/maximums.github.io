package com.cdodi.webidl.gradle

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

/**
 * ```
 * webIdl {
 *     idlFiles.from("idl/webgpu.idl")
 *     packageName = "com.cdodi.webgpu.bindings"
 *     runtimePackage = "com.cdodi.webgpu.runtime"
 *     externalClass("OffscreenCanvas", "org.w3c.dom.OffscreenCanvas")
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

    /**
     * Types the IDL uses but does not define: IDL name -> `"class|interface|value <Kotlin type>"`.
     * Starts with [com.cdodi.webidl.model.ExternalType.DEFAULTS]; any other unknown name fails the build.
     */
    abstract val externalTypes: MapProperty<String, String>

    /** An IDL interface extending it becomes an `external abstract class`. */
    fun externalClass(idlName: String, kotlinClass: String) = externalTypes.put(idlName, "class $kotlinClass")

    fun externalInterface(idlName: String, kotlinInterface: String) = externalTypes.put(idlName, "interface $kotlinInterface")

    /** Only used as a value; dropped when it appears as a supertype. A trailing `?` makes it nullable. */
    fun externalValue(idlName: String, kotlinType: String) = externalTypes.put(idlName, "value $kotlinType")
}
