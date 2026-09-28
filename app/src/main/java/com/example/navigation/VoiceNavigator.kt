package com.example.navigation

import android.content.Context
import com.example.data.model.RouteStep
import kotlinx.coroutines.flow.StateFlow

/**
 * VoiceNavigator delegates turn-by-turn announcements and speech engine
 * lifecycle to NavigationSpeechService.
 */
class VoiceNavigator(context: Context) {

    private val speechService = NavigationSpeechService(context)

    val isMuted: StateFlow<Boolean> = speechService.isMuted
    val isSpeaking: StateFlow<Boolean> = speechService.isSpeaking

    fun toggleMute(): Boolean = speechService.toggleMute()

    fun speak(text: String, isPriority: Boolean = false) {
        speechService.speak(text, isPriority)
    }

    fun announceManeuver(step: RouteStep, distanceMeters: Double) {
        speechService.announceManeuver(step, distanceMeters)
    }

    fun announceArrival(houseNumber: String?, entranceNote: String? = null) {
        speechService.announceArrival(houseNumber, entranceNote)
    }

    fun announceReroute() {
        speechService.announceReroute()
    }

    fun announceRouteSummary(distanceText: String, durationText: String) {
        speechService.announceRouteSummary(distanceText, durationText)
    }

    fun announceNavigationStarted(firstInstruction: String?) {
        speechService.announceNavigationStarted(firstInstruction)
    }

    fun stop() {
        speechService.stop()
    }

    fun destroy() {
        speechService.destroy()
    }
}
