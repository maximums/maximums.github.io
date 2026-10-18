// Compose resources (the WGSL shaders, for one) are copied next to the test bundle, but Karma doesn't serve them.
// Serve that folder, and send the path the app asks for (./composeResources/...) there.
config.files.push({ pattern: "kotlin/composeResources/**/*", included: false, served: true, watched: false });
config.proxies = Object.assign({}, config.proxies, { "/composeResources/": "/base/kotlin/composeResources/" });
