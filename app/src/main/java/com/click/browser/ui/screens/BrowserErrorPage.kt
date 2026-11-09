package com.click.browser.ui.screens

/** Retry only safe, idempotent top-level GET requests to web origins. */
internal fun browserRetryUrl(method: String, url: String?): String? {
    if (!method.equals("GET", ignoreCase = true)) return null
    return url?.takeIf(::isHttpRetryUrl)
}

/** Build the local network-error page without script execution or unsafe HTML interpolation. */
internal fun browserErrorPageHtml(description: String?, retryUrl: String?): String {
    val safeDescription = (description?.take(200) ?: "Unknown error").escapeBrowserErrorHtml()
    val retryControl = retryUrl
        ?.takeIf(::isHttpRetryUrl)
        ?.let { url ->
            "<a class=\"btn\" href=\"${url.escapeBrowserErrorHtml()}\">Retry Connection</a>"
        }
        ?: "<p>Retry is not available for this request.</p>"

    return """
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
                body { background-color: #FBF8F1; color: #302A22; font-family: sans-serif; text-align: center; padding: 50px; }
                h1 { color: #66533F; font-size: 24px; }
                p { color: #393228; font-size: 16px; }
                .btn { display: inline-block; background-color: #66533F; border: none; color: #FFFEFB; padding: 12px 24px; border-radius: 8px; font-weight: bold; margin-top: 20px; text-decoration: none; }
            </style>
        </head>
        <body>
            <h1>⚠️ Unable to load page</h1>
            <p>Click Browser could not reach the server or network is offline.</p>
            <p><i>Details: $safeDescription</i></p>
            $retryControl
        </body>
        </html>
    """.trimIndent()
}

private fun isHttpRetryUrl(url: String): Boolean {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd <= 0) return false
    val scheme = url.substring(0, schemeEnd)
    if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) return false
    val authority = url.substring(schemeEnd + 3).substringBefore('/').substringBefore('?').substringBefore('#')
    return authority.isNotBlank()
}

private fun String.escapeBrowserErrorHtml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
