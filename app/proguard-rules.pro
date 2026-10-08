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
