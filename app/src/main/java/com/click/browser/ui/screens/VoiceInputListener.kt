package com.click.browser.ui.screens

import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer

/**
 * Named [RecognitionListener] for AI-chat voice input.
 *
 * Kept as a named class (not an anonymous object) so the R8/ProGuard rules
 * can target it explicitly — release builds run with minification and the
 * SpeechRecognizer callback path must survive obfuscation.
 * See app/proguard-rules.pro.
 */
class VoiceInputListener(
    private val onResult: (String) -> Unit,
    private val onEnd: () -> Unit
) : RecognitionListener {

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit

    override fun onError(error: Int) {
        // Any error (no match, no speech, network…) just ends the session;
        // the UI stays on whatever text was already typed.
        onEnd()
    }

    override fun onResults(results: Bundle?) {
        val heard = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
        if (heard.isNotBlank()) onResult(heard)
        onEnd()
    }

    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
