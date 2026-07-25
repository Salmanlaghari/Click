package com.click.browser.engine

object AntiDetectionInjections {

    const val INJECT_10_LAYERS = """
        (function() {
            if (window.antiDetectionInjected) return;
            window.antiDetectionInjected = true;

            // 1. User Agent Spoofing (Win11 Desktop Chrome)
            const fakeUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";
            Object.defineProperty(navigator, 'userAgent', { get: () => fakeUA });
            Object.defineProperty(navigator, 'appVersion', { get: () => fakeUA });

            // 2. Platform Spoofing
            Object.defineProperty(navigator, 'platform', { get: () => 'Win32' });

            // 3. Touch Support Spoofing
            Object.defineProperty(navigator, 'maxTouchPoints', { get: () => 0 });
            Object.defineProperty(navigator, 'msMaxTouchPoints', { get: () => 0 });
            // Remove ontouchstart/ontouchend
            delete window.ontouchstart;
            delete window.ontouchend;

            // 4. Screen Size Spoofing (1920x1080)
            Object.defineProperty(window.screen, 'width', { get: () => 1920 });
            Object.defineProperty(window.screen, 'height', { get: () => 1080 });
            Object.defineProperty(window.screen, 'availWidth', { get: () => 1920 });
            Object.defineProperty(window.screen, 'availHeight', { get: () => 1040 });

            // 5. Device Pixel Ratio Spoofing
            Object.defineProperty(window, 'devicePixelRatio', { get: () => 1 });

            // 6. Hardware Concurrency Spoofing
            Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => 8 });

            // 7. Pointer Type / Mouse-only detection spoofing
            if (window.PointerEvent) {
                const originalGetParameter = WebGLRenderingContext.prototype.getParameter;
                // Add hooks if needed
            }

            // 8. WebGL Renderer Spoofing (Desktop GPU)
            const getParameterProxy = function(parameter) {
                // UNMASKED_VENDOR_WEBGL = 37445, UNMASKED_RENDERER_WEBGL = 37446
                if (parameter === 37445) {
                    return "Google Inc. (NVIDIA)";
                }
                if (parameter === 37446) {
                    return "ANGLE (NVIDIA, NVIDIA GeForce RTX 4070 Ti/PCIe/SSE2, OpenGL 4.5)";
                }
                return originalGetParameter.apply(this, [parameter]);
            };
            const originalGetParameter = WebGLRenderingContext.prototype.getParameter;
            WebGLRenderingContext.prototype.getParameter = getParameterProxy;
            if (window.WebGL2RenderingContext) {
                WebGL2RenderingContext.prototype.getParameter = getParameterProxy;
            }

            // 9. Plugins List Spoofing
            const fakePlugins = [
                { name: "PDF Viewer", filename: "internal-pdf-viewer", description: "Portable Document Format" },
                { name: "Chrome PDF Viewer", filename: "internal-pdf-viewer", description: "Portable Document Format" }
            ];
            Object.defineProperty(navigator, 'plugins', { get: () => fakePlugins });

            // 10. Language Spoofing
            Object.defineProperty(navigator, 'language', { get: () => 'en-US' });
            Object.defineProperty(navigator, 'languages', { get: () => ['en-US', 'en'] });
        })();
    """

    // Video grabber script to auto-detect video elements on any page
    const val VIDEO_GRABBER_JS = """
        (function() {
            setInterval(function() {
                const videos = [];
                document.querySelectorAll('video').forEach(function(v) {
                    if (v.src) {
                        videos.push(v.src);
                    }
                    const sources = v.querySelectorAll('source');
                    sources.forEach(function(s) {
                        if (s.src) {
                            videos.push(s.src);
                        }
                    });
                });
                // Also parse standard iframes or elements with media extensions
                document.querySelectorAll('a').forEach(function(a) {
                    if (a.href && (a.href.indexOf('.mp4') !== -1 || a.href.indexOf('.webm') !== -1)) {
                        videos.push(a.href);
                    }
                });

                if (videos.length > 0 && window.VideoGrabberBridge) {
                    window.VideoGrabberBridge.onVideosDetected(JSON.stringify([...new Set(videos)]));
                }
            }, 2000);
        })();
    """
}
