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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.MlKitTranslator
import com.click.browser.engine.ModeTheme
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * On-device translation sheet (ML Kit). Models download on demand —
 * nothing is bundled in the APK. Supports typed text and full-page
 * translation via JS text-node extraction.
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
    var sourceLang by remember { mutableStateOf(TranslateLanguage.ENGLISH) }
    var targetLang by remember { mutableStateOf(MlKitTranslator.defaultTargetLanguage()) }
    var inputText by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pageProgress by remember { mutableStateOf<Float?>(null) }

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
                    "Translate (on-device)",
                    color = theme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = theme.onSurface)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguagePicker(
                    label = "From",
                    selected = sourceLang,
                    onSelect = { sourceLang = it },
                    theme = theme,
                    modifier = Modifier.weight(1f)
                )
                LanguagePicker(
                    label = "To",
                    selected = targetLang,
                    onSelect = { targetLang = it },
                    theme = theme,
                    modifier = Modifier.weight(1f)
                )
            }

            if (status.isNotEmpty()) {
                Text(status, color = theme.primary, fontSize = 13.sp)
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Text to translate") },
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
                colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
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
                Text(
                    resultText,
                    color = theme.onSurface,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                )
            }

            // ---- Page translation ----
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
                Button(
                    onClick = {
                        if (busy) return@Button
                        busy = true
                        pageProgress = 0f
                        status = ""
                        scope.launch {
                            try {
                                val ok = MlKitTranslator.ensureModel(sourceLang, targetLang) { s ->
                                    status = s
                                }
                                if (!ok) {
                                    status = "Couldn't download the language model. Check your connection."
                                } else {
                                    translatePageInPlace(webView, sourceLang, targetLang) { done, total ->
                                        pageProgress = if (total == 0) 1f else done.toFloat() / total
                                    }
                                    status = "Page translated ✓"
                                }
                            } catch (e: Exception) {
                                status = "Page translation failed: ${e.message?.take(80)}"
                            } finally {
                                busy = false
                                pageProgress = null
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.secondary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Translate this page")
                }
                Text(
                    "Replaces visible text in place. Reload the page to restore the original.",
                    color = theme.onSurface.copy(alpha = 0.5f),
                    fontSize = 12.sp
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

/** JS: collect visible text nodes (same filter must be used for inject). */
private const val EXTRACT_JS = """(function(){
var out=[];
var w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,{acceptNode:function(n){
var t=n.nodeValue;if(!t||!t.trim())return NodeFilter.FILTER_REJECT;
var p=n.parentElement;if(p&&/^(SCRIPT|STYLE|NOSCRIPT|TEXTAREA|INPUT|SELECT|OPTION)$/.test(p.tagName))return NodeFilter.FILTER_REJECT;
return NodeFilter.FILTER_ACCEPT;}});
var i=0;while(w.nextNode()&&i<400){out.push({i:i,t:w.currentNode.nodeValue});i++;}
return JSON.stringify(out);})();"""

/**
 * Extracts visible text nodes via JS, translates them with ML Kit, and
 * writes them back in place. Suspends on IO/default dispatchers; all
 * WebView calls happen on the main thread.
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
            // Translate in small batches on Default to keep the UI alive.
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
    val injectJs = """(function(mapStr){
var map=JSON.parse(mapStr);
var w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,{acceptNode:function(n){
var t=n.nodeValue;if(!t||!t.trim())return NodeFilter.FILTER_REJECT;
var p=n.parentElement;if(p&&/^(SCRIPT|STYLE|NOSCRIPT|TEXTAREA|INPUT|SELECT|OPTION)$/.test(p.tagName))return NodeFilter.FILTER_REJECT;
return NodeFilter.FILTER_ACCEPT;}});
var i=0;while(w.nextNode()&&i<400){var v=map[i];if(v!=null)w.currentNode.nodeValue=v;i++;}})($mapJson);"""
    withContext(Dispatchers.Main) {
        webView.evaluateJavascript(injectJs, null)
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
