package com.cdodi.gltf

/** A file this reader can't read, or one that breaks the glTF spec; the message says what and where. */
class GltfException(message: String, cause: Throwable? = null) : Exception(message, cause)
