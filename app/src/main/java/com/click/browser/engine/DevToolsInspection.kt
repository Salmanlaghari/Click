package com.click.browser.engine

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

internal data class DevToolsDomNode(
    val path: List<Int>,
    val tagName: String,
    val id: String,
    val classes: List<String>,
    val elementType: String?,
    val childCount: Int,
    val children: List<DevToolsDomNode>
) {
    val pathKey: String get() = path.joinToString("/")
}

internal data class DevToolsDomSnapshot(
    val root: DevToolsDomNode?,
    val truncated: Boolean
)

internal data class DevToolsComputedStyles(
    val tagName: String,
    val properties: Map<String, String>
)

/**
 * Safe, bounded inspection helpers for the Developer-mode WebView panel.
 * The automatic DOM snapshot intentionally omits text, arbitrary attributes,
 * form-control values, cookies, and storage. Console execution is separate and
 * is only invoked after the user explicitly consents in the panel.
 */
internal object DevToolsInspectionPolicy {
    const val MAX_DOM_NODES = 500
    const val MAX_DOM_DEPTH = 24
    const val MAX_DOM_RESULT_LENGTH = 512_000
    const val MAX_SOURCE_REFERENCES = 200
    const val MAX_SOURCE_SCAN_NODES = 2_000
    const val MAX_SOURCE_URL_LENGTH = 512
    const val MAX_SOURCE_RESULT_LENGTH = 200_000
    const val MAX_RAW_URL_LENGTH = 4_096
    const val MAX_DISPLAYED_URL_PATH_SEGMENTS = 24
    const val MAX_COMMAND_LENGTH = 1_000
    const val MAX_RESULT_LENGTH = 4_000
    const val MAX_CONSOLE_ITEMS = 40
    const val MAX_CONSOLE_DEPTH = 4
    const val MAX_CONSOLE_NODES = 100
    const val MAX_CONSOLE_STRING_LENGTH = 256
    const val MAX_CONSOLE_RESULT_LENGTH = 32_000
    const val MAX_STYLE_RESULT_LENGTH = 24_000
    const val MAX_RENDERED_DOM_NODES = 500
    const val MAX_RENDERED_DOM_DEPTH = 24
    const val MAX_RENDERED_DOM_ATTRIBUTES_PER_NODE = 64
    const val MAX_RENDERED_DOM_SOURCE_LENGTH = 24_000
    const val MAX_RENDERED_DOM_RESULT_LENGTH = 128_000

    fun canInspectPage(
        mode: BrowserMode,
        engine: BrowserTabEngine,
        panelVisible: Boolean,
        pageLoaded: Boolean,
        targetMatches: Boolean
    ): Boolean = panelVisible && pageLoaded && targetMatches &&
        DevToolsAvailabilityPolicy.canCapture(mode, engine)

    fun isInspectableWebPageUrl(rawUrl: String?): Boolean {
        val uri = runCatching { URI(rawUrl?.trim().orEmpty()) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        return scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
    }

    fun shouldResetPageSessionForUrlChange(previousUrl: String, updatedUrl: String): Boolean =
        updatedUrl.isNotBlank() && previousUrl != updatedUrl

    fun acceptsSessionResult(
        capturedGeneration: Long,
        currentGeneration: Long,
        targetMatches: Boolean,
        sessionActive: Boolean
    ): Boolean = capturedGeneration == currentGeneration && targetMatches && sessionActive

    fun shouldCaptureSelectedTabData(
        mode: BrowserMode,
        engine: BrowserTabEngine,
        panelVisible: Boolean,
        pageLoaded: Boolean,
        targetMatches: Boolean,
        selectedTab: Int,
        requiredTab: Int
    ): Boolean = selectedTab == requiredTab && canInspectPage(
        mode = mode,
        engine = engine,
        panelVisible = panelVisible,
        pageLoaded = pageLoaded,
        targetMatches = targetMatches
    )

    fun shouldCapturePageConsoleMessages(
        mode: BrowserMode,
        engine: BrowserTabEngine,
        panelVisible: Boolean,
        pageLoaded: Boolean,
        targetMatches: Boolean,
        selectedTab: Int,
        consentGranted: Boolean
    ): Boolean = consentGranted && shouldCaptureSelectedTabData(
        mode = mode,
        engine = engine,
        panelVisible = panelVisible,
        pageLoaded = pageLoaded,
        targetMatches = targetMatches,
        selectedTab = selectedTab,
        requiredTab = 1
    )

    fun shouldCaptureRenderedDom(
        mode: BrowserMode,
        engine: BrowserTabEngine,
        panelVisible: Boolean,
        pageLoaded: Boolean,
        targetMatches: Boolean,
        selectedTab: Int,
        userConfirmed: Boolean
    ): Boolean = userConfirmed && shouldCaptureSelectedTabData(
        mode = mode,
        engine = engine,
        panelVisible = panelVisible,
        pageLoaded = pageLoaded,
        targetMatches = targetMatches,
        selectedTab = selectedTab,
        requiredTab = 3
    )

    fun acceptsRenderedDomResult(
        capturedGeneration: Long,
        currentGeneration: Long,
        capturedTabIndex: Int,
        currentTabIndex: Int,
        capturedTabId: String,
        currentTabId: String,
        capturedTabUrl: String,
        currentTabUrl: String,
        capturedPageUrl: String,
        currentPageUrl: String,
        targetMatches: Boolean,
        sessionActive: Boolean
    ): Boolean = capturedTabIndex == currentTabIndex &&
        capturedTabId == currentTabId &&
        capturedTabUrl == currentTabUrl &&
        capturedPageUrl == currentPageUrl &&
        acceptsSessionResult(
            capturedGeneration = capturedGeneration,
            currentGeneration = currentGeneration,
            targetMatches = targetMatches,
            sessionActive = sessionActive
        )

    // Fixed read-only allowlist for common layout and presentation values.
    // URL-bearing shorthands like `background` and `background-image` are omitted.
    val computedStyleProperties = listOf(
        "display", "position", "color", "background-color",
        "font-family", "font-size", "font-style", "font-weight",
        "line-height", "letter-spacing", "text-align", "text-decoration-line",
        "text-transform", "white-space", "text-overflow", "width",
        "height", "min-width", "max-width", "min-height",
        "max-height", "box-sizing", "overflow", "overflow-x",
        "overflow-y", "margin-top", "margin-right", "margin-bottom",
        "margin-left", "padding-top", "padding-right", "padding-bottom",
        "padding-left", "top", "right", "bottom", "left",
        "z-index", "opacity", "visibility", "border-top-width",
        "border-top-style", "border-top-color", "border-width", "border-style",
        "border-color", "border-radius", "box-shadow", "flex-direction",
        "flex-wrap", "flex-grow", "flex-shrink", "flex-basis", "align-items",
        "align-content", "align-self", "justify-content", "justify-items",
        "justify-self", "place-items", "place-content", "place-self", "gap",
        "row-gap", "column-gap", "grid-template-columns", "grid-template-rows",
        "grid-auto-flow", "grid-auto-columns", "grid-auto-rows", "grid-column",
        "grid-row", "object-fit", "object-position", "transform", "transform-origin"
    )

    val domSnapshotScript: String = """
        (function(){
          try {
            var maxNodes = $MAX_DOM_NODES;
            var maxDepth = $MAX_DOM_DEPTH;
              var count = 0;
              var truncated = false;
              var root = document.documentElement;
              if (!root) return JSON.stringify({root:null,truncated:false});
              function cleanLabel(value, limit) {
                return String(value || "").slice(0, limit).replace(/[\x00-\x1f\x7f]/g, " ");
              }
              function walk(element, path, depth) {
              if (!element || count >= maxNodes || depth > maxDepth) {
                truncated = true;
                return null;
              }
              count++;
              var tag = cleanLabel(String(element.localName || "unknown").toLowerCase(), 64);
              var id = "";
              var className = "";
              var type = null;
              try { id = cleanLabel(element.getAttribute("id"), 160); } catch (_) {}
              try { className = cleanLabel(element.getAttribute("class"), 240).split(/\s+/).filter(Boolean).slice(0, 12).join(" "); } catch (_) {}
              if (tag === "input" || tag === "button" || tag === "select" || tag === "textarea") {
                try { type = cleanLabel(element.getAttribute("type"), 32); } catch (_) {}
              }
              var children = [];
              var childElements = element.children;
              var childCount = childElements ? childElements.length : 0;
              for (var i = 0; i < childCount; i++) {
                if (count >= maxNodes) { truncated = true; break; }
                var child = walk(childElements[i], path.concat(i), depth + 1);
                if (child) children.push(child);
              }
              return {tag:tag,id:id,className:className,type:type,path:path,childCount:childCount,children:children};
            }
            return JSON.stringify({root:walk(root, [], 0),truncated:truncated});
          } catch (_) {
            return JSON.stringify({root:null,truncated:false});
          }
        })()
    """.trimIndent()

    /** Enumerates only a bounded set of source URLs without materializing a page-wide NodeList. */
    val sourceReferencesScript: String = """
        (function(){try{
          var root=document.documentElement;
          if(!root)return '[]';
          var walker=document.createTreeWalker(root,NodeFilter.SHOW_ELEMENT);
          var refs=[],visited=0,node;
          var maxScan=$MAX_SOURCE_SCAN_NODES;
          var maxReferences=$MAX_SOURCE_REFERENCES;
          var maxUrlLength=$MAX_SOURCE_URL_LENGTH;
          while(visited<maxScan&&refs.length<maxReferences){
            node=walker.nextNode();
            if(!node)break;
            visited++;
            var tag=String(node.localName||'').toLowerCase();
            var url='';
            if((tag==='script'||tag==='img')&&node.getAttribute('src'))url=node.src||node.getAttribute('src');
            else if(tag==='link'&&(node.rel||'').toLowerCase().split(/\s+/).indexOf('stylesheet')>=0&&node.getAttribute('href'))url=node.href||node.getAttribute('href');
            if(url)refs.push(String(url).slice(0,maxUrlLength));
          }
          return JSON.stringify(refs);
        }catch(_){return '[]';}})()
    """.trimIndent()

    /** Reads only the current rendered DOM after explicit UI confirmation; never fetches linked resources. */
    val renderedDomSourceScript: String = """
        (function(){try{
          var maxNodes=$MAX_RENDERED_DOM_NODES;
          var maxDepth=$MAX_RENDERED_DOM_DEPTH;
          var maxAttributesPerNode=$MAX_RENDERED_DOM_ATTRIBUTES_PER_NODE;
          var maxChars=$MAX_RENDERED_DOM_SOURCE_LENGTH;
          var pieces=[],length=0,visited=0,truncated=false;
          var omitted={script:1,style:1,noscript:1,iframe:1,frame:1,object:1,embed:1,template:1};
          var voidTags={area:1,base:1,br:1,col:1,hr:1,img:1,input:1,link:1,meta:1,source:1,track:1,wbr:1};
          function append(value){
            var text=String(value||'');
            var remaining=maxChars-length;
            if(remaining<=0){truncated=true;return;}
            if(text.length>remaining){text=text.slice(0,remaining);truncated=true;}
            pieces.push(text);length+=text.length;
          }
          function cleanText(value){
            var text=String(value||'');
            var clean='';
            for(var index=0;index<text.length;index++){
              var code=text.charCodeAt(index);
              clean+=((code<32&&code!==9&&code!==10&&code!==13)||code===127)?' ':text.charAt(index);
            }
            return clean;
          }
          function escapeMarkup(value){
            return cleanText(value).split('&').join('&amp;').split('<').join('&lt;')
              .split('>').join('&gt;').split('\"').join('&quot;');
          }
          function safeLabel(value,limit){
            var text=cleanText(value).slice(0,limit);
            var at=text.indexOf('@');
            if(at>0&&text.indexOf('.',at)>at+1)return '[redacted]';
            var tokens=text.split(/[^A-Za-z0-9_-]+/);
            for(var tokenIndex=0;tokenIndex<tokens.length;tokenIndex++){
              var token=tokens[tokenIndex];
              var mixedLettersAndDigits=/[A-Za-z]/.test(token)&&/[0-9]/.test(token);
              if(token.length>=24||(token.length>=16&&mixedLettersAndDigits))return '[redacted]';
            }
            return text;
          }
          function safeUrl(value){
            try{
              var url=new URL(String(value||''),document.baseURI);
              if(url.protocol!=='http:'&&url.protocol!=='https:')return '[URL omitted]';
              return url.origin+'/[path omitted]';
            }catch(_){return '[URL omitted]';}
          }
          function walk(node,depth){
            if(!node||depth>maxDepth||visited>=maxNodes||length>=maxChars){truncated=true;return;}
            visited++;
            if(node.nodeType===3){
              var rawText=String(node.nodeValue||'');
              var text=rawText.slice(0,maxChars-length);
              if(text.length<rawText.length)truncated=true;
              append(escapeMarkup(text));
              return;
            }
            if(node.nodeType!==1)return;
            var tag=String(node.localName||'').toLowerCase();
            if(!tag||omitted[tag])return;
            append('<'+tag);
            var attributes=node.attributes;
            if(attributes&&attributes.length>maxAttributesPerNode)truncated=true;
            for(var i=0;attributes&&i<attributes.length&&i<maxAttributesPerNode&&length<maxChars;i++){
              var name=String(attributes[i].name||'').toLowerCase();
              if(name==='id'||name==='class'||name==='role'||name==='type'||name==='href'||name==='src'){
                var raw=String(attributes[i].value||'').slice(0,512);
                var value=(name==='href'||name==='src')?safeUrl(raw):safeLabel(raw,128);
                append(' '+name+'='+String.fromCharCode(34)+escapeMarkup(value)+String.fromCharCode(34));
              }
            }
            append('>');
            if(voidTags[tag])return;
            if(tag==='input'||tag==='textarea'||tag==='select'||tag==='option'||tag==='output'){
              append('[form value omitted]');
            }else if(node.isContentEditable){
              append('[editable content omitted]');
            }else{
              var children=node.childNodes;
              for(var childIndex=0;children&&childIndex<children.length;childIndex++){
                if(visited>=maxNodes||length>=maxChars){truncated=true;break;}
                walk(children[childIndex],depth+1);
              }
            }
            append('</'+tag+'>');
          }
          var root=document.documentElement;
          if(!root)return JSON.stringify({markup:'',truncated:false});
          walk(root,0);
          return JSON.stringify({markup:pieces.join(''),truncated:truncated});
        }catch(_){return JSON.stringify({markup:'',truncated:false});}})()
    """.trimIndent()

    fun buildComputedStylesScript(path: List<Int>): String? {
        if (path.size > MAX_DOM_DEPTH || path.any { it < 0 || it > 100_000 }) return null
        val pathLiteral = path.joinToString(prefix = "[", postfix = "]")
        val propertiesLiteral = computedStyleProperties.joinToString(
            prefix = "[",
            postfix = "]"
        ) { JSONObject.quote(it) }
        return """
            (function(){
              try {
                var path = $pathLiteral;
                var element = document.documentElement;
                for (var i = 0; i < path.length; i++) {
                  if (!element || !element.children || path[i] >= element.children.length) return JSON.stringify({});
                  element = element.children[path[i]];
                }
                if (!element) return JSON.stringify({});
                var style = getComputedStyle(element);
                var properties = $propertiesLiteral;
                var result = {};
                for (var j = 0; j < properties.length; j++) {
                  result[properties[j]] = style.getPropertyValue(properties[j]).slice(0, 128);
                }
                return JSON.stringify({tag:String(element.localName || "unknown").toLowerCase(),properties:result});
              } catch (_) {
                return JSON.stringify({});
              }
            })()
        """.trimIndent()
    }

    /** Wraps one explicitly consented JavaScript expression as a JS string, never raw source interpolation. */
    fun buildConsoleEvaluationScript(command: String): String? {
        val trimmed = command.trim().take(MAX_COMMAND_LENGTH)
        if (trimmed.isEmpty()) return null
        val quotedCommand = JSONObject.quote(trimmed)
        return """
            (function(){
              try {
                var result = (new Function("return (" + $quotedCommand + ")"))();
                if (result && typeof result.then === "function") {
                  return JSON.stringify({kind:"error",value:"Promise results are not supported."});
                }
                function isSensitiveKey(key) {
                  return typeof key === "string" && /(value|cookie|pass(word|phrase)?|secret|token|authorization|credential|api[_-]?key|access[_-]?key)/i.test(key);
                }
                function safeString(value) {
                  var text = String(value);
                  if (/^[^\s=;]{1,80}=[^;]*(;\s*[^\s=;]{1,80}=[^;]*)*$/.test(text)) return "[redacted cookie-like value]";
                  return text.length > $MAX_CONSOLE_STRING_LENGTH ? text.slice(0, $MAX_CONSOLE_STRING_LENGTH) + "…" : text;
                }
                var seen = new WeakSet();
                function boundedSnapshot(value, depth, state) {
                  if (state.remaining <= 0) return "[truncated]";
                  state.remaining--;
                  if (value === null || typeof value === "boolean" || typeof value === "number") return value;
                  if (typeof value === "string") return safeString(value);
                  if (typeof value === "undefined") return "undefined";
                  if (typeof value === "function") return "[Function]";
                  if (typeof value !== "object") return safeString(value);
                  if (depth >= $MAX_CONSOLE_DEPTH) return "[depth limit]";
                  if (seen.has(value)) return "[Repeated reference]";
                  seen.add(value);
                  if (Array.isArray(value)) {
                    var array = [];
                    var length = Math.min(value.length, $MAX_CONSOLE_ITEMS);
                    for (var i = 0; i < length; i++) {
                      if (state.remaining <= 0) { array.push("[truncated]"); break; }
                      var arrayDescriptor = Object.getOwnPropertyDescriptor(value, String(i));
                      if (!arrayDescriptor) array.push("[empty]");
                      else if (!("value" in arrayDescriptor)) array.push("[accessor]");
                      else array.push(boundedSnapshot(arrayDescriptor.value, depth + 1, state));
                    }
                    if (value.length > length && array.length <= $MAX_CONSOLE_ITEMS) array.push("[truncated]");
                    return array;
                  }
                  var object = Object.create(null);
                  var count = 0;
                  for (var key in value) {
                    if (!Object.prototype.hasOwnProperty.call(value, key)) continue;
                    if (count >= $MAX_CONSOLE_ITEMS || state.remaining <= 0) { object["…"] = "[truncated]"; break; }
                    if (isSensitiveKey(key)) object[key] = "[redacted]";
                    else {
                      var descriptor = Object.getOwnPropertyDescriptor(value, key);
                      object[key] = !descriptor || !("value" in descriptor)
                        ? "[accessor]"
                        : boundedSnapshot(descriptor.value, depth + 1, state);
                    }
                    count++;
                  }
                  return object;
                }
                var output;
                if (result === undefined) output = "undefined";
                else if (result === null) output = "null";
                else if (typeof result === "string") output = safeString(result);
                else if (typeof result === "function") output = "[Function]";
                else {
                  try {
                    output = JSON.stringify(boundedSnapshot(result, 0, {remaining:$MAX_CONSOLE_NODES}));
                    if (output === undefined) output = String(result);
                  } catch (_) { output = "[unserializable result]"; }
                }
                return JSON.stringify({kind:"result",value:String(output).slice(0, $MAX_RESULT_LENGTH)});
              } catch (error) {
                var message = String(error && error.message || error).slice(0, $MAX_RESULT_LENGTH);
                return JSON.stringify({kind:"error",value:message});
              }
            })()
        """.trimIndent()
    }

    fun parseDomSnapshot(evaluateJavascriptResult: String?): DevToolsDomSnapshot {
        if (evaluateJavascriptResult.orEmpty().length > MAX_DOM_RESULT_LENGTH) {
            return DevToolsDomSnapshot(root = null, truncated = true)
        }
        val jsonText = decodeEvaluateString(evaluateJavascriptResult)
        if (jsonText.isBlank()) return DevToolsDomSnapshot(root = null, truncated = false)
        if (jsonText.length > MAX_DOM_RESULT_LENGTH) return DevToolsDomSnapshot(root = null, truncated = true)
        return runCatching {
            val container = JSONObject(jsonText)
            val remaining = intArrayOf(MAX_DOM_NODES)
            val root = container.optJSONObject("root")?.let { parseNode(it, remaining, 0) }
            DevToolsDomSnapshot(
                root = root,
                truncated = container.optBoolean("truncated") || remaining[0] <= 0
            )
        }.getOrElse { DevToolsDomSnapshot(root = null, truncated = false) }
    }

    fun parseComputedStyles(evaluateJavascriptResult: String?): DevToolsComputedStyles? {
        if (evaluateJavascriptResult.orEmpty().length > MAX_STYLE_RESULT_LENGTH) return null
        val jsonText = decodeEvaluateString(evaluateJavascriptResult)
        if (jsonText.isBlank() || jsonText.length > MAX_STYLE_RESULT_LENGTH) return null
        return runCatching {
            val json = JSONObject(jsonText)
            val propertiesJson = json.optJSONObject("properties") ?: return null
            val properties = computedStyleProperties.mapNotNull { name ->
                propertiesJson.optString(name).takeIf { it.isNotBlank() }?.let { name to it.take(128) }
            }.toMap()
            DevToolsComputedStyles(
                tagName = safeDomLabel(json.optString("tag"), 64),
                properties = properties
            )
        }.getOrNull()
    }

    fun parseConsoleResult(evaluateJavascriptResult: String?): String {
        if (evaluateJavascriptResult.orEmpty().length > MAX_CONSOLE_RESULT_LENGTH) {
            return "Result was too large to display."
        }
        val jsonText = decodeEvaluateString(evaluateJavascriptResult)
        if (jsonText.isBlank()) return "No result."
        if (jsonText.length > MAX_CONSOLE_RESULT_LENGTH) return "Result was too large to display."
        return runCatching {
            val json = JSONObject(jsonText)
            val value = json.optString("value", "No result.").take(MAX_RESULT_LENGTH)
            if (json.optString("kind") == "error") "Error: $value" else value
        }.getOrElse { jsonText.take(MAX_RESULT_LENGTH) }
    }

    fun parseSourceReferences(evaluateJavascriptResult: String?): List<String> {
        if (evaluateJavascriptResult.orEmpty().length > MAX_SOURCE_RESULT_LENGTH) return emptyList()
        val jsonText = decodeEvaluateString(evaluateJavascriptResult)
        if (jsonText.isBlank() || jsonText.length > MAX_SOURCE_RESULT_LENGTH) return emptyList()
        return runCatching {
            val array = JSONArray(jsonText)
            (0 until minOf(array.length(), MAX_SOURCE_REFERENCES))
                .map { sanitizeUrl(array.optString(it).take(MAX_SOURCE_URL_LENGTH)) }
                .filter { it != "URL hidden" }
                .distinct()
        }.getOrDefault(emptyList())
    }

    fun parseRenderedDomSource(evaluateJavascriptResult: String?): String? {
        if (evaluateJavascriptResult.orEmpty().length > MAX_RENDERED_DOM_RESULT_LENGTH) return null
        val jsonText = decodeEvaluateString(evaluateJavascriptResult)
        if (jsonText.isBlank() || jsonText.length > MAX_RENDERED_DOM_RESULT_LENGTH) return null
        return runCatching {
            val json = JSONObject(jsonText)
            val markup = json.optString("markup")
            when {
                markup.isBlank() || markup.length > MAX_RENDERED_DOM_SOURCE_LENGTH -> null
                json.optBoolean("truncated") ->
                    "$markup\n<!-- Rendered DOM preview truncated by safety limits (character, node, depth, or attribute cap) -->"
                else -> markup
            }
        }.getOrNull()
    }

    /** Removes URL credentials, query parameters, fragments, and token-like path data. */
    fun sanitizeUrl(rawUrl: String): String {
        if (rawUrl.length > MAX_RAW_URL_LENGTH) return "URL hidden"
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return "URL hidden"
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        if (scheme !in setOf("http", "https") || host.isBlank()) return "URL hidden"
        val port = uri.port.takeIf { it >= 0 && !(scheme == "https" && it == 443) && !(scheme == "http" && it == 80) }
            ?.let { ":$it" }.orEmpty()
        val sensitivePathKeys = setOf(
            "token", "access_token", "refresh_token", "api_key", "apikey", "key",
            "secret", "authorization", "session", "password", "code", "auth"
        )
        var redactNextSegment = false
        val safePathSegments = uri.rawPath.orEmpty().split('/').map { segment ->
            if (segment.isEmpty()) {
                segment
            } else {
                val decodedSegment = runCatching {
                    URLDecoder.decode(segment.replace("+", "%2B"), Charsets.UTF_8.name())
                }
                    .getOrDefault(segment)
                val normalizedKey = decodedSegment.substringBefore('=').lowercase()
                val keyWithInlineValue = decodedSegment.contains('=') && normalizedKey in sensitivePathKeys
                val redactCurrent = redactNextSegment || keyWithInlineValue
                redactNextSegment = !decodedSegment.contains('=') && normalizedKey in sensitivePathKeys
                val hasNoWhitespaceOrControls = decodedSegment.none { it.isWhitespace() || it.isISOControl() }
                val hasMixedLettersAndDigits = decodedSegment.any { it.isLetter() } && decodedSegment.any { it.isDigit() }
                val tokenLike = hasNoWhitespaceOrControls && (
                    (decodedSegment.length >= 16 && hasMixedLettersAndDigits) ||
                        (decodedSegment.length >= 24 && decodedSegment.any { it.isLetterOrDigit() })
                    )
                when {
                    redactCurrent || segment.length > 40 || decodedSegment.contains('@') || tokenLike -> "[redacted]"
                    else -> segment.take(128)
                }
            }
        }
        val pathSegments = if (safePathSegments.size > MAX_DISPLAYED_URL_PATH_SEGMENTS) {
            val edgeCount = MAX_DISPLAYED_URL_PATH_SEGMENTS / 2
            safePathSegments.take(edgeCount) + "…" + safePathSegments.takeLast(edgeCount)
        } else {
            safePathSegments
        }
        val path = pathSegments.joinToString("/")
        return "$scheme://$host$port${path.ifBlank { "/" }}"
    }

    private fun parseNode(json: JSONObject, remaining: IntArray, depth: Int): DevToolsDomNode? {
        if (remaining[0] <= 0 || depth > MAX_DOM_DEPTH) return null
        remaining[0]--
        val pathJson = json.optJSONArray("path") ?: JSONArray()
        val path = (0 until pathJson.length())
            .take(MAX_DOM_DEPTH)
            .map { pathJson.optInt(it, -1) }
            .filter { it >= 0 }
        val rawClasses = json.optString("className")
        val classes = rawClasses.split(Regex("\\s+"))
            .asSequence()
            .filter { it.isNotBlank() }
            .take(12)
            .map { safeDomLabel(it, 48) }
            .toList()
        val childrenJson = json.optJSONArray("children") ?: JSONArray()
        val children = ArrayList<DevToolsDomNode>(minOf(childrenJson.length(), remaining[0]))
        for (index in 0 until childrenJson.length()) {
            if (remaining[0] <= 0) break
            val child = childrenJson.optJSONObject(index)?.let { parseNode(it, remaining, depth + 1) }
            if (child != null) children += child
        }
        return DevToolsDomNode(
            path = path,
            tagName = safeDomLabel(json.optString("tag", "unknown"), 64).ifBlank { "unknown" },
            id = safeDomLabel(json.optString("id"), 80),
            classes = classes,
            elementType = json.optString("type").takeIf { it.isNotBlank() }?.let { safeDomLabel(it, 32) },
            childCount = json.optInt("childCount", children.size).coerceAtLeast(0),
            children = children
        )
    }

    private fun decodeEvaluateString(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null") return ""
        return runCatching { JSONArray("[$raw]").optString(0, "") }.getOrElse { raw }
    }

    private fun safeDomLabel(raw: String, maxLength: Int): String {
        val value = raw.replace(Regex("[\\r\\n\\t]"), " ").trim().take(maxLength)
        val email = Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b")
        val longToken = Regex("\\b[A-Za-z0-9_-]{32,}\\b")
        return if (email.containsMatchIn(value) || longToken.containsMatchIn(value)) "[redacted]" else value
    }
}
