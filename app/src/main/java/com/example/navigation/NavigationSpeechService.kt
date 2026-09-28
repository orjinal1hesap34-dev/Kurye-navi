package com.example.navigation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.data.model.RouteStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Service class that integrates the Android TextToSpeech engine to announce
 * real-time, turn-by-turn navigation instructions, maneuver warnings, and arrival
 * details for the courier.
 */
class NavigationSpeechService(
    context: Context
) : TextToSpeech.OnInitListener {

    private val appContext: Context = context.applicationContext
    private var tts: TextToSpeech? = TextToSpeech(appContext, this)
    private var isInitialized = false

    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var lastSpokenText: String = ""
    private var lastSpokenTime: Long = 0L

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Configure Turkish language for courier announcements
            val turkishLocale = Locale.forLanguageTag("tr-TR")
            val langResult = tts?.setLanguage(turkishLocale)

            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to Turkish generic locale or system default
                val altResult = tts?.setLanguage(Locale.forLanguageTag("tr"))
                if (altResult == TextToSpeech.LANG_MISSING_DATA || altResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.getDefault()
                }
            }

            // Set speech attributes: slightly brisk, crisp cadence suitable for helmets/earpieces
            tts?.setSpeechRate(1.05f)
            tts?.setPitch(1.0f)

            // Setup audio attributes for navigation guidance
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(audioAttributes)

            // Attach listener to track speaking state
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    abandonAudioFocus()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    abandonAudioFocus()
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                    abandonAudioFocus()
                }
            })

            isInitialized = true
        }
    }

    /**
     * Toggles mute state and announces state change.
     */
    fun toggleMute(): Boolean {
        _isMuted.value = !_isMuted.value
        if (_isMuted.value) {
            stop()
        } else {
            speak("Sesli yönlendirme açıldı", isPriority = true)
        }
        return _isMuted.value
    }

    /**
     * Direct speak command with queue handling, deduplication, and audio focus.
     */
    fun speak(text: String, isPriority: Boolean = false) {
        if (_isMuted.value || !isInitialized || text.isBlank()) return

        val now = System.currentTimeMillis()
        // Prevent repetitive vocalization within 8 seconds unless priority
        if (!isPriority && text == lastSpokenText && (now - lastSpokenTime) < 8000) {
            return
        }

        lastSpokenText = text
        lastSpokenTime = now

        requestAudioFocus()

        val queueMode = if (isPriority) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val utteranceId = "KuryeVoice_${System.currentTimeMillis()}"

        tts?.speak(text, queueMode, null, utteranceId)
    }

    /**
     * Announces a turn maneuver with upcoming distance warning.
     */
    fun announceManeuver(step: RouteStep, distanceMeters: Double) {
        val roundedDist = when {
            distanceMeters > 1000 -> "${String.format(Locale.US, "%.1f", distanceMeters / 1000)} kilometre"
            distanceMeters > 150 -> "${(distanceMeters / 50).toInt() * 50} metre"
            distanceMeters > 30 -> "${(distanceMeters / 10).toInt() * 10} metre"
            else -> "şimdi"
        }

        val speech = if (roundedDist == "şimdi") {
            "Şimdi ${step.instruction}"
        } else {
            "$roundedDist sonra ${step.instruction}"
        }

        speak(speech, isPriority = distanceMeters <= 40)
    }

    /**
     * Announces arrival at delivery destination with door number information.
     */
    fun announceArrival(houseNumber: String?, entranceNote: String? = null) {
        val message = when {
            !houseNumber.isNullOrBlank() && !entranceNote.isNullOrBlank() ->
                "Hedefe ulaştınız! Dış kapı numarası: $houseNumber. Giriş: $entranceNote."
            !houseNumber.isNullOrBlank() ->
                "Hedefe ulaştınız! Dış kapı numarası: $houseNumber. Teslimat noktasına vardınız."
            else ->
                "Hedefe ulaştınız! Teslimat noktasına vardınız."
        }
        speak(message, isPriority = true)
    }

    /**
     * Announces off-route detection and route recalculation.
     */
    fun announceReroute() {
        speak("Rotadan sapıldı, yeni rota hesaplanıyor.", isPriority = true)
    }

    /**
     * Announces route creation summary (distance and estimated duration).
     */
    fun announceRouteSummary(distanceText: String, durationText: String) {
        val message = "Rota hazır. Mesafe: $distanceText, tahmini varış süresi $durationText."
        speak(message, isPriority = false)
    }

    /**
     * Announces immediate navigation commencement.
     */
    fun announceNavigationStarted(firstInstruction: String?) {
        val message = "Navigasyon başlatıldı. ${firstInstruction ?: "Rotayı takip edin."}"
        speak(message, isPriority = true)
    }

    /**
     * Immediately stops ongoing speech output.
     */
    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
        abandonAudioFocus()
    }

    /**
     * Releases TextToSpeech resources and shuts down the engine.
     */
    fun destroy() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                audioManager?.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    null,
                    AudioManager.STREAM_NOTIFICATION,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            // Non-critical audio focus error
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Focus will naturally yield on completion
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
}
