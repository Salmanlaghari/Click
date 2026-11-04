package com.click.browser.ui.screens

import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.edit
import com.click.browser.engine.AppSettings
import com.click.browser.engine.MlKitTranslator
import com.click.browser.engine.ModeTheme
import com.click.browser.engine.dataStore
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * On-device translation sheet (ML Kit). Models download on demand —
 * nothing is bundled in the APK. Supports typed text and full-page
 * translation via JS text-node extraction, RTL layout switching for
 * Urdu/Arabic, auto-translate preference, and in-place original restore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateSheet(
    theme: ModeTheme,
    webView: WebView?,
    pageUrl: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var sourceLang by remember { mutableStateOf(TranslateLanguage.ENGLISH) }
    var targetLang by remember { mutableStateOf(MlKitTranslator.defaultTargetLanguage()) }
    var autoTranslatePages by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pageTranslated by remember { mutableStateOf(false) }
    var pageProgress by remember { mutableStateOf<Float?>(null) }

    fun saveTargetPreference(code: String) {
        targetLang = code
        scope.launch {
            try {
                context.dataStore.edit { it[AppSettings.TRANSLATE_TARGET] = code }
            } catch (_: Exception) { }
        }
    }

    fun startPageTranslation(target: String = targetLang) {
        if (busy) return
        busy = true
        pageProgress = 0f
        status = ""
        scope.launch {
            try {
                val ok = MlKitTranslator.ensureModel(sourceLang, target) { s ->
                    status = s
                }
                if (!ok) {
                    status = "Couldn't download the language model. Check your connection."
                } else {
                    val wv = webView
                    if (wv == null) {
                        status = "No page loaded to translate."
                    } else {
                        translatePageInPlace(wv, sourceLang, target) { done, total ->
                            pageProgress = if (total == 0) 1f else done.toFloat() / total
                        }
                        pageTranslated = true
                        status = "Page translated to ${MlKitTranslator.languageName(target)} ✓"
                    }
                }
            } catch (e: Exception) {
                status = "Page translation failed: ${e.message?.take(80)}"
            } finally {
                busy = false
                pageProgress = null
            }
        }
    }

    // Settings → Language: honor the saved "Translate to" language, then
    // auto-start page translation when "Auto-translate pages" is on.
    var autoStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!autoStarted) {
            autoStarted = true
            try {
                val prefs = context.dataStore.data.first()
                val saved = prefs[AppSettings.TRANSLATE_TARGET]
                val autoPref = prefs[AppSettings.TRANSLATE_AUTO] == true
                autoTranslatePages = autoPref
                val effectiveTarget = if (!saved.isNullOrBlank()) saved else targetLang
                if (!saved.isNullOrBlank()) targetLang = saved
                if (autoPref && webView != null && pageUrl.startsWith("http")) {
                    startPageTranslation(effectiveTarget)
                }
            } catch (_: Exception) { }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = theme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Translate, contentDescription = null, tint = theme.primary)
                Spacer(Modifier.size(8.dp))
                Text(
                    "Translate Page & Text",
                    color = theme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = theme.onSurface)
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LanguagePicker(
                    label = "From",
                    selected = sourceLang,
                    onSelect = { sourceLang = it },
                    theme = theme,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        val previousSource = sourceLang
                        sourceLang = targetLang
                        saveTargetPreference(previousSource)
                    },
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Icon(
                        Icons.Filled.SwapHoriz,
                        contentDescription = "Swap languages",
                        tint = theme.primary
                    )
                }
                LanguagePicker(
                    label = "To",
                    selected = targetLang,
                    onSelect = { saveTargetPreference(it) },
                    theme = theme,
                    modifier = Modifier.weight(1f)
                )
            }

            if (status.isNotEmpty()) {
                Text(status, color = theme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            // ---- Full-page translation controls (shown first when browsing a live page) ----
            if (webView != null && pageUrl.startsWith("http")) {
                if (pageProgress != null) {
                    LinearProgressIndicator(
                        progress = { pageProgress ?: 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = theme.primary
                    )
                    Text(
                        "Translating page… ${((pageProgress ?: 0f) * 100).toInt()}%",
                        color = theme.onSurface.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { startPageTranslation() },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Translate this page")
                    }
                    if (pageTranslated) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    restoreOriginalPageInPlace(webView)
                                    pageTranslated = false
                                    status = "Restored original page"
                                }
                            },
                            enabled = !busy,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = theme.primary
                            )
                            Spacer(Modifier.size(4.dp))
                            Text("Original", color = theme.primary)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Auto-translate web pages",
                            color = theme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Automatically translate opened pages into ${MlKitTranslator.languageName(targetLang)}",
                            color = theme.onSurface.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = autoTranslatePages,
                        onCheckedChange = { enabled ->
                            autoTranslatePages = enabled
                            scope.launch {
                                try {
                                    context.dataStore.edit { it[AppSettings.TRANSLATE_AUTO] = enabled }
                                } catch (_: Exception) { }
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = theme.primary)
                    )
                }
            } else if (pageUrl.startsWith("http")) {
                Text(
                    "For GeckoView pages, page translation is not connected yet. You can still translate text below; the page will not be modified.",
                    color = theme.onSurface.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Or enter custom text to translate") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = theme.onSurface,
                    unfocusedTextColor = theme.onSurface,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )

            Button(
                onClick = {
                    if (inputText.isBlank() || busy) return@Button
                    busy = true
                    status = ""
                    scope.launch {
                        try {
                            val ok = MlKitTranslator.ensureModel(sourceLang, targetLang) { s ->
                                status = s
                            }
                            if (!ok) {
                                status = "Couldn't download the language model. Check your connection."
                            } else {
                                status = "Translating…"
                                resultText = MlKitTranslator.translate(inputText, sourceLang, targetLang)
                                status = ""
                            }
                        } catch (e: Exception) {
                            status = "Translation failed: ${e.message?.take(80)}"
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = theme.secondary),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (busy && pageProgress == null) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Text("Translate text")
            }

            if (resultText.isNotEmpty()) {
                val isRtlTarget = targetLang == TranslateLanguage.URDU || targetLang == TranslateLanguage.ARABIC
                Text(
                    resultText,
                    color = theme.onSurface,
                    fontSize = 15.sp,
                    textAlign = if (isRtlTarget) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePicker(
    label: String,
    selected: String,
    onSelect: (String) -> Unit,
    theme: ModeTheme,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = MlKitTranslator.languageName(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedTextColor = theme.onSurface,
                unfocusedTextColor = theme.onSurface
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            MlKitTranslator.offeredLanguages.forEach { (code, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onSelect(code); expanded = false }
                )
            }
        }
    }
}

/** JS: collect visible text nodes and cache originals once so restore does not require a reload. */
private const val EXTRACT_JS = """(function(){
var out=[];
var orig=window.__clickOrigTextNodes||{};
var hasOrig=Object.keys(orig).length>0;
if(!window.__clickOrigDir){window.__clickOrigDir=document.documentElement.getAttribute('dir')||'';}
var w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,{acceptNode:function(n){
var t=n.nodeValue;if(!t||!t.trim())return NodeFilter.FILTER_REJECT;
var p=n.parentElement;if(p&&/^(SCRIPT|STYLE|NOSCRIPT|TEXTAREA|INPUT|SELECT|OPTION)$/.test(p.tagName))return NodeFilter.FILTER_REJECT;
return NodeFilter.FILTER_ACCEPT;}});
var i=0;while(w.nextNode()&&i<400){
var raw=hasOrig&&orig[i]!=null?orig[i]:w.currentNode.nodeValue;
if(!hasOrig){orig[i]=raw;}
out.push({i:i,t:raw});
i++;
}
window.__clickOrigTextNodes=orig;
return JSON.stringify(out);})();"""

private const val RESTORE_JS = """(function(){
var orig=window.__clickOrigTextNodes;
if(!orig)return;
var w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,{acceptNode:function(n){
var t=n.nodeValue;if(!t||!t.trim())return NodeFilter.FILTER_REJECT;
var p=n.parentElement;if(p&&/^(SCRIPT|STYLE|NOSCRIPT|TEXTAREA|INPUT|SELECT|OPTION)$/.test(p.tagName))return NodeFilter.FILTER_REJECT;
return NodeFilter.FILTER_ACCEPT;}});
var i=0;while(w.nextNode()&&i<400){if(orig[i]!=null)w.currentNode.nodeValue=orig[i];i++;}
if(window.__clickOrigDir){document.documentElement.setAttribute('dir',window.__clickOrigDir);}
else{document.documentElement.removeAttribute('dir');}
})();"""

/**
 * Extracts visible text nodes via JS, translates them with ML Kit, and
 * writes them back in place with automatic RTL direction for Urdu/Arabic.
 */
private suspend fun translatePageInPlace(
    webView: WebView,
    source: String,
    target: String,
    onProgress: (done: Int, total: Int) -> Unit
) {
    if (source == target) return
    val rawJson: String = withContext(Dispatchers.Main) {
        suspendCancellable(webView, EXTRACT_JS)
    } ?: return
    val arr = try { JSONArray(rawJson) } catch (e: Exception) { return }
    val total = arr.length()
    if (total == 0) return
    val translated = JSONObject()
    for (i in 0 until total) {
        val obj = arr.optJSONObject(i) ?: continue
        val text = obj.optString("t")
        if (text.isBlank()) {
            onProgress(i + 1, total)
            continue
        }
        try {
            val out = withContext(Dispatchers.Default) {
                MlKitTranslator.translate(text, source, target)
            }
            translated.put(obj.getInt("i").toString(), out)
        } catch (e: Exception) {
            // Keep original text for this node on failure.
        }
        onProgress(i + 1, total)
    }
    val mapJson = JSONObject.quote(translated.toString())
    val dirValue = if (target == TranslateLanguage.URDU || target == TranslateLanguage.ARABIC) "rtl" else "ltr"
    val injectJs = """(function(mapStr){
var map=JSON.parse(mapStr);
document.documentElement.setAttribute('dir','$dirValue');
var w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,{acceptNode:function(n){
var t=n.nodeValue;if(!t||!t.trim())return NodeFilter.FILTER_REJECT;
var p=n.parentElement;if(p&&/^(SCRIPT|STYLE|NOSCRIPT|TEXTAREA|INPUT|SELECT|OPTION)$/.test(p.tagName))return NodeFilter.FILTER_REJECT;
return NodeFilter.FILTER_ACCEPT;}});
var i=0;while(w.nextNode()&&i<400){var v=map[i];if(v!=null)w.currentNode.nodeValue=v;i++;}})($mapJson);"""
    withContext(Dispatchers.Main) {
        webView.evaluateJavascript(injectJs, null)
    }
}

private suspend fun restoreOriginalPageInPlace(webView: WebView) {
    withContext(Dispatchers.Main) {
        webView.evaluateJavascript(RESTORE_JS, null)
    }
}

/** evaluateJavascript as a suspend fun. Must be called on the main thread. */
private suspend fun suspendCancellable(webView: WebView, js: String): String? =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        webView.evaluateJavascript(js) { value ->
            // The sheet may be dismissed (coroutine cancelled) before the JS
            // callback fires — never resume a dead continuation.
            if (cont.isActive) {
                // evaluateJavascript returns the JS result JSON-encoded; our
                // script returns a string, so decode one layer.
                val unquoted = try {
                    if (value == null || value == "null") null
                    else org.json.JSONTokener(value).nextValue() as? String
                } catch (e: Exception) {
                    null
                }
                cont.resume(unquoted) {}
            }
        }
    }
