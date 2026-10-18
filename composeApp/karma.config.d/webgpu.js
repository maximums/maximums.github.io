// Headless Chrome with WebGPU enabled.
// Based on "Chrome", not "ChromeHeadless": karma-chrome-launcher's ChromeHeadless preset adds --disable-gpu,
// which turns WebGPU off. Where there is no usable GPU (e.g. CI runners) the SwiftShader software adapter
// is allowed, so the smoke test can still run.
config.set({
    browsers: ["ChromeHeadlessWebGPU"],
    customLaunchers: {
        ChromeHeadlessWebGPU: {
            base: "Chrome",
            flags: [
                "--headless=new",
                "--no-first-run",
                "--no-default-browser-check",
                "--enable-unsafe-webgpu",
                "--enable-unsafe-swiftshader",
                "--enable-features=Vulkan",
            ],
        },
    },
});

// Requesting an adapter and compiling a pipeline takes longer than Mocha's 2 s default.
config.client = config.client || {};
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 30000 });
