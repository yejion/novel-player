package com.example.player

data class PlayerState(
    val bookId: Long? = null,
    val bookTitle: String = "",
    val coverColorHex: String = "#FF6200EE",
    val currentTrackIndex: Int = 0,
    val currentTrackTitle: String = "未在播放",
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val skipIntroSeconds: Int = -1, // per book. -1 means global
    val skipOutroSeconds: Int = -1, // per book. -1 means global
    val globalIntroSeconds: Int = 15,
    val globalOutroSeconds: Int = 10
) {
    // Computed helper values
    val currentSkipIntroSec: Int 
        get() = if (skipIntroSeconds == -1) globalIntroSeconds else skipIntroSeconds

    val currentSkipOutroSec: Int 
        get() = if (skipOutroSeconds == -1) globalOutroSeconds else skipOutroSeconds
}
