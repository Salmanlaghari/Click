package com.click.browser.engine

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device translation via ML Kit. Language models are NEVER bundled in the
 * APK — they are downloaded on demand when the user first picks a language
 * pair, then cached on device for offline use.
 */
object MlKitTranslator {

    /**
     * Languages offered in the UI: code -> display name.
     * Loaded from assets/translate/languages.json when available (so the list
     * can grow via an asset update), with this built-in list as fallback.
     * Call [loadLanguages] once at startup; safe to call repeatedly.
     */
    private val builtInLanguages: List<Pair<String, String>> = listOf(
        TranslateLanguage.ENGLISH to "English",
        TranslateLanguage.URDU to "Urdu",
        TranslateLanguage.ARABIC to "Arabic",
        TranslateLanguage.HINDI to "Hindi",
        TranslateLanguage.CHINESE to "Chinese",
        TranslateLanguage.FRENCH to "French",
        TranslateLanguage.GERMAN to "German",
        TranslateLanguage.SPANISH to "Spanish",
        TranslateLanguage.TURKISH to "Turkish",
        TranslateLanguage.RUSSIAN to "Russian"
    )

    @Volatile
    private var assetLanguages: List<Pair<String, String>>? = null

    val offeredLanguages: List<Pair<String, String>>
        get() = assetLanguages ?: builtInLanguages

    fun loadLanguages(context: android.content.Context) {
        if (assetLanguages != null) return
        try {
            val json = context.assets.open("translate/languages.json").use { ins ->
                org.json.JSONObject(ins.readBytes().toString(Charsets.UTF_8))
            }
            val arr = json.optJSONArray("languages") ?: return
            val list = ArrayList<Pair<String, String>>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val code = o.optString("code").trim()
                val name = o.optString("name").trim()
                // Only advertise languages ML Kit actually supports.
                if (code.isNotEmpty() && name.isNotEmpty()) {
                    val supported = try {
                        TranslateLanguage.fromLanguageTag(code) != null
                    } catch (e: Exception) {
                        false
                    }
                    if (supported) list.add(code to name)
                    else android.util.Log.w("MlKitTranslator", "Unsupported language in asset: $code")
                }
            }
            if (list.isNotEmpty()) assetLanguages = list
        } catch (e: Exception) {
            android.util.Log.w("MlKitTranslator", "Could not load languages.json, using built-in list", e)
        }
    }

    /** Best-effort: the device language if ML Kit supports it, else Urdu. */
    fun defaultTargetLanguage(): String {
        val tag = Locale.getDefault().language
        return try {
            TranslateLanguage.fromLanguageTag(tag) ?: TranslateLanguage.URDU
        } catch (e: Exception) {
            TranslateLanguage.URDU
        }
    }

    fun languageName(code: String): String =
        offeredLanguages.firstOrNull { it.first == code }?.second ?: code

    private fun clientFor(source: String, target: String) =
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .build()
        )

    /**
     * Ensures both the source and target models are on device, downloading
     * them on demand when missing. [onStatus] is only invoked when an actual
     * download happens — cached models return silently with no spinner.
     * ML Kit does not expose byte-level progress, so [onStatus] reports
     * stages honestly. Returns true when the models are ready.
     */
    suspend fun ensureModel(
        source: String,
        target: String,
        requireWifi: Boolean = false,
        onStatus: (String) -> Unit = {}
    ): Boolean {
        val client = clientFor(source, target)
        return try {
            if (areBothModelsDownloaded(source, target)) {
                true
            } else {
                onStatus("Downloading ${languageName(target)} model…")
                val conditions = DownloadConditions.Builder()
                    .apply { if (requireWifi) requireWifi() }
                    .build()
                suspendCancellableCoroutine<Boolean> { cont ->
                    client.downloadModelIfNeeded(conditions)
                        .addOnSuccessListener { cont.resume(true) }
                        .addOnFailureListener { e -> cont.resumeWithException(e) }
                }
                onStatus("Model ready")
                true
            }
        } catch (e: Exception) {
            android.util.Log.e("MlKitTranslator", "ensureModel failed", e)
            false
        } finally {
            client.close()
        }
    }

    /**
     * True only when BOTH the source and target models are already on device.
     * Translation needs both — checking just the target would report ready
     * while the source model is still missing.
     */
    private suspend fun areBothModelsDownloaded(source: String, target: String): Boolean {
        return try {
            val manager = com.google.mlkit.common.model.RemoteModelManager.getInstance()
            val sourceModel =
                com.google.mlkit.nl.translate.TranslateRemoteModel.Builder(source).build()
            val targetModel =
                com.google.mlkit.nl.translate.TranslateRemoteModel.Builder(target).build()
            suspendCancellableCoroutine { cont ->
                manager.isModelDownloaded(sourceModel)
                    .addOnSuccessListener { sourceReady ->
                        manager.isModelDownloaded(targetModel)
                            .addOnSuccessListener { targetReady ->
                                cont.resume(sourceReady && targetReady)
                            }
                            .addOnFailureListener { e -> cont.resumeWithException(e) }
                    }
                    .addOnFailureListener { e -> cont.resumeWithException(e) }
            }
        } catch (e: Exception) {
            // If the check itself fails, fall through to downloadModelIfNeeded
            // (idempotent) rather than misreporting "ready".
            android.util.Log.w("MlKitTranslator", "model check failed, will attempt download", e)
            false
        }
    }

    /** Translates [text]. Caller must have ensured the model via [ensureModel]. */
    suspend fun translate(text: String, source: String, target: String): String {
        if (text.isBlank()) return text
        if (source == target) return text
        val client = clientFor(source, target)
        return try {
            suspendCancellableCoroutine { cont ->
                client.translate(text)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
        } finally {
            client.close()
        }
    }
}
