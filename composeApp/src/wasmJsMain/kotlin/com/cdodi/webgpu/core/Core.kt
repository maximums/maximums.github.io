package com.cdodi.webgpu.core

import com.cdodi.webgpu.bindings.GPU

@JsFun("() => navigator.gpu")
external fun gpu(): GPU?