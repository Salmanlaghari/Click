# Click Browser — R8/ProGuard rules (release builds run with minify + shrinkResources).
#
# These rules exist so R8's obfuscation doesn't break runtime behavior.
# OkHttp, DataStore, and AndroidX WebKit ship their own consumer rules;
# what we must keep explicitly is anything called by name from JavaScript.

# WebView JavascriptInterface bridges (VideoGrabberBridge, DevToolsBridge,
# UserscriptBridge, WebRtcTestBridge): JS calls these methods BY NAME via
# addJavascriptInterface, so neither the methods nor their names may be
# renamed or stripped.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclasseswithmembernames class * {
    @android.webkit.JavascriptInterface <methods>;
}
# Keep the bridge classes themselves instantiable.
-keep class com.click.browser.engine.*Bridge { *; }

# BuildConfig fields are read reflectively by nothing, but keep the class
# itself to avoid any AGP/R8 edge cases with generated code.
-keep class com.click.browser.BuildConfig { *; }

# AI-chat voice input: the SpeechRecognizer callback listener must survive
# minification — the framework invokes these methods on the registered
# listener instance.
-keep class com.click.browser.ui.screens.VoiceInputListener { *; }

# ML Kit Translation: keep only the API surface we actually call
# (ML Kit ships its own consumer rules for internals). Narrowed per review —
# the old blanket keeps blocked obfuscation of whole packages.
-keep class com.google.mlkit.nl.translate.Translation { *; }
-keep class com.google.mlkit.nl.translate.Translator { *; }
-keep class com.google.mlkit.nl.translate.TranslatorOptions { *; }
-keep class com.google.mlkit.nl.translate.TranslatorOptions$Builder { *; }
-keep class com.google.mlkit.nl.translate.TranslateLanguage { *; }
-keep class com.google.mlkit.common.model.DownloadConditions { *; }
-keep class com.google.mlkit.common.model.DownloadConditions$Builder { *; }
