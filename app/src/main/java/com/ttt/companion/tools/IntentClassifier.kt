package com.ttt.companion.tools

import java.util.Locale

/**
 * Lightweight logic to detect if the user query suggests tool usage.
 */
object IntentClassifier {

    private val KEYWORDS = mapOf(
        "alarm" to listOf("alarm", "wake me", "wake-up"),
        "timer" to listOf("timer", "countdown", "seconds", "minutes"),
        "torch" to listOf("torch", "flashlight", "light"),
        "vibrate" to listOf("vibrate", "buzz"),
        "apps" to listOf("open", "launch", "start app", "youtube", "maps", "gmail", "camera", "browser", "spotify", "music")
    )

    /**
     * Returns a set of tool keys that might be relevant to the [query].
     */
    fun classify(query: String): Set<String> {
        val lowerQuery = query.lowercase(Locale.ROOT)
        return KEYWORDS.filter { (_, keywords) ->
            keywords.any { lowerQuery.contains(it) }
        }.keys
    }
}
