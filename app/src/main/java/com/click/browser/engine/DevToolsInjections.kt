package com.click.browser.engine

object DevToolsInjections {

    // JS Injection to hijack console methods and send them to the Native Bridge
    const val CONSOLE_HIJACK = """
        (function() {
            if (window.consoleHijacked) return;
            window.consoleHijacked = true;

            const originalLog = console.log;
            const originalWarn = console.warn;
            const originalError = console.error;
            const originalInfo = console.info;

            console.log = function(...args) {
                originalLog.apply(console, args);
                if (window.DevToolsBridge) {
                    window.DevToolsBridge.log('success', args.join(' '));
                }
            };

            console.warn = function(...args) {
                originalWarn.apply(console, args);
                if (window.DevToolsBridge) {
                    window.DevToolsBridge.log('warning', args.join(' '));
                }
            };

            console.error = function(...args) {
                originalError.apply(console, args);
                if (window.DevToolsBridge) {
                    window.DevToolsBridge.log('error', args.join(' '));
                }
            };

            console.info = function(...args) {
                originalInfo.apply(console, args);
                if (window.DevToolsBridge) {
                    window.DevToolsBridge.log('info', args.join(' '));
                }
            };

            // Also listen to unhandled errors
            window.addEventListener('error', function(e) {
                if (window.DevToolsBridge) {
                    window.DevToolsBridge.log('error', e.message + ' at ' + e.filename + ':' + e.lineno);
                }
            });
        })();
    """

    // JS Injection to hook into fetch & XMLHttpRequest to intercept network requests
    const val NETWORK_INTERCEPT = """
        (function() {
            if (window.networkIntercepted) return;
            window.networkIntercepted = true;

            // XML Http Request Intercept
            const open = XMLHttpRequest.prototype.open;
            const send = XMLHttpRequest.prototype.send;

            XMLHttpRequest.prototype.open = function(method, url, ...args) {
                this._method = method;
                this._url = url;
                this._startTime = performance.now();
                return open.apply(this, [method, url, ...args]);
            };

            XMLHttpRequest.prototype.send = function(...args) {
                this.addEventListener('load', function() {
                    const duration = Math.round(performance.now() - this._startTime);
                    const size = this.responseText ? (this.responseText.length + ' B') : '0 B';
                    if (window.DevToolsBridge) {
                        window.DevToolsBridge.network(this._method || 'GET', this._url, this.status, duration, size);
                    }
                });
                return send.apply(this, args);
            };

            // Fetch Intercept
            const originalFetch = window.fetch;
            window.fetch = async function(resource, init) {
                const startTime = performance.now();
                const method = (init && init.method) || 'GET';
                const url = typeof resource === 'string' ? resource : resource.url;
                try {
                    const response = await originalFetch(resource, init);
                    const duration = Math.round(performance.now() - startTime);
                    const clone = response.clone();
                    let size = 'unknown';
                    try {
                        const txt = await clone.text();
                        size = txt.length + ' B';
                    } catch(e) {}
                    if (window.DevToolsBridge) {
                        window.DevToolsBridge.network(method, url, response.status, duration, size);
                    }
                    return response;
                } catch (error) {
                    const duration = Math.round(performance.now() - startTime);
                    if (window.DevToolsBridge) {
                        window.DevToolsBridge.network(method, url, 0, duration, 'Failed');
                    }
                    throw error;
                }
            };
        })();
    """

    // JS Injection to inspect elements & collect DOM/Sources
    const val GET_DOM = "javascript:if(window.DevToolsBridge) { window.DevToolsBridge.dom(document.documentElement.outerHTML); }"

    const val GET_SOURCES = """
        (function() {
            const list = [];
            document.querySelectorAll('script[src]').forEach(el => list.push(el.src));
            document.querySelectorAll('link[rel="stylesheet"]').forEach(el => list.push(el.href));
            document.querySelectorAll('img[src]').forEach(el => list.push(el.src));
            if (window.DevToolsBridge) {
                window.DevToolsBridge.sources(JSON.stringify(list));
            }
        })();
    """

    // Script to support element click inspecting
    const val ELEMENT_INSPECTOR_ENABLE = """
        (function() {
            if (window.elementInspectorEnabled) return;
            window.elementInspectorEnabled = true;

            window._inspectorClickHandler = function(e) {
                e.preventDefault();
                e.stopPropagation();

                // Highlight element briefly
                const originalOutline = e.target.style.outline;
                e.target.style.outline = '3px solid purple';
                setTimeout(() => {
                    e.target.style.outline = originalOutline;
                }, 1500);

                // Capture element properties and send to Elements / Console
                const selector = e.target.tagName.toLowerCase() + (e.target.id ? '#' + e.target.id : '') + (e.target.className ? '.' + e.target.className.split(' ').join('.') : '');
                const styles = window.getComputedStyle(e.target);
                const cssInfo = "Selector: " + selector + " | CSS: color=" + styles.color + " display=" + styles.display + " margin=" + styles.margin;

                if (window.DevToolsBridge) {
                    window.DevToolsBridge.log('info', '[INSPECTED] ' + cssInfo);
                    window.DevToolsBridge.dom(e.target.outerHTML);
                }
            };

            document.addEventListener('click', window._inspectorClickHandler, true);
        })();
    """

    const val ELEMENT_INSPECTOR_DISABLE = """
        (function() {
            if (!window.elementInspectorEnabled) return;
            window.elementInspectorEnabled = false;
            if (window._inspectorClickHandler) {
                document.removeEventListener('click', window._inspectorClickHandler, true);
            }
        })();
    """
}
