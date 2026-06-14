package com.example.player

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.io.File

@OptIn(UnstableApi::class)
class AudiobookPlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    lateinit var player: ExoPlayer

    // DB and Repository
    private val database by lazy { AudiobookDatabase.getInstance(applicationContext) }
    private val repository by lazy { AudiobookRepository(database) }

    // Service state
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressJob: Job? = null

    // Currently playing info
    var currentBook: Book? = null
        private set
    var currentTracks: List<Track> = emptyList()
        private set
    
    // Config values
    private var globalIntroSec = 15
    private var globalOutroSec = 10

    // Observables
    private val _serviceState = MutableStateFlow<PlayerState>(PlayerState())
    val serviceState: StateFlow<PlayerState> = _serviceState.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): AudiobookPlayerService = this@AudiobookPlayerService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder? {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("PlayerService", "onCreate called")

        // 1. Initialize ExoPlayer with AudioAttributes setting
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH) // Optimized for Audiobooks
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .build()

        // 2. Build MediaSession. Point Session Activity to our MainActivity
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()

        // 3. Listen to player events
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                Log.d("PlayerService", "onMediaItemTransition index=${player.currentMediaItemIndex}")
                handleMediaItemTransition()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                Log.d("PlayerService", "onIsPlayingChanged state=$isPlaying")
                if (isPlaying) {
                     startPollingProgress()
                } else {
                     stopPollingProgress()
                }
                updateUiState()
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                super.onPlaybackParametersChanged(playbackParameters)
                updateUiState()
            }

            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                Log.e("PlayerService", "ExoPlayer Error: ${error.message}", error)
            }
        })

        // 4. Load Global Settings
        serviceScope.launch {
            repository.globalSettings.collect { settings ->
                if (settings != null) {
                    globalIntroSec = settings.defaultSkipIntroSeconds
                    globalOutroSec = settings.defaultSkipOutroSeconds
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    // 4. Core Audiobook Playback functions

    fun playBook(book: Book, tracks: List<Track>, startingIndex: Int = -1) {
        if (tracks.isEmpty()) return
        
        currentBook = book
        currentTracks = tracks.sortedBy { it.trackNumber }

        val actualIndex = if (startingIndex >= 0) startingIndex else book.currentTrackIndex
        val resumePosition = if (startingIndex >= 0) 0L else book.lastPlayedPositionMs

        player.stop()
        player.clearMediaItems()

        // Assemble playlist
        currentTracks.forEach { track ->
            val mediaItem = MediaItem.Builder()
                .setMediaId(track.id.toString())
                .setUri(track.filePath)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(book.title)
                        .setAlbumTitle(book.title)
                        .build()
                )
                .build()
            player.addMediaItem(mediaItem)
        }

        // Seek and prepare
        player.seekTo(actualIndex.coerceIn(0, currentTracks.size - 1), resumePosition)
        player.prepare()
        player.play()

        updateUiState()
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.play()
        }
        updateUiState()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        updateUiState()
    }

    fun seekForward() {
        player.seekTo(player.currentPosition + 15000L) // +15s
        updateUiState()
    }

    fun seekBackward() {
        player.seekTo(player.currentPosition - 15000L) // -15s
        updateUiState()
    }

    fun changePlaybackSpeed(speed: Float) {
        val params = PlaybackParameters(speed)
        player.setPlaybackSpeed(speed)
        updateUiState()
    }

    fun playNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        }
    }

    fun playPrevious() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        }
    }

    // 5. Skip intro and outro routines

    private fun handleMediaItemTransition() {
        val book = currentBook ?: return
        val currentIdx = player.currentMediaItemIndex
        if (currentIdx < 0 || currentIdx >= currentTracks.size) return

        // 1. Identify Skip Intro value
        val skipIntroSec = if (book.skipIntroSeconds == -1) globalIntroSec else book.skipIntroSeconds
        
        Log.d("PlayerService", "Transitioning: skipIntroSec=$skipIntroSec, positionMs=${player.currentPosition}")

        if (skipIntroSec > 0) {
            val currentPos = player.currentPosition
            val skipIntroMs = skipIntroSec * 1000L
            if (currentPos < skipIntroMs) {
                player.seekTo(skipIntroMs)
                Log.d("PlayerService", "Auto-skipped Intro: Seeking to $skipIntroMs ms")
            }
        }

        // Save progress whenever media item changes
        saveHistory(currentIdx, player.currentPosition)
        updateUiState()
    }

    private fun startPollingProgress() {
        progressJob?.cancel()
        progressJob = serviceScope.launch {
            while (isActive) {
                delay(400) // Poll every 400ms to stay responsive
                checkSkipOutroAndHistory()
            }
        }
    }

    private fun stopPollingProgress() {
        progressJob?.cancel()
        progressJob = null
        val book = currentBook ?: return
        saveHistory(player.currentMediaItemIndex, player.currentPosition)
    }

    private fun checkSkipOutroAndHistory() {
        val book = currentBook ?: return
        val currentIdx = player.currentMediaItemIndex
        if (currentIdx < 0 || currentIdx >= currentTracks.size) return

        val duration = player.duration
        val position = player.currentPosition

        if (duration > 0) {
            // Check Skip Outro
            val skipOutroSec = if (book.skipOutroSeconds == -1) globalOutroSec else book.skipOutroSeconds
            val skipOutroMs = skipOutroSec * 1000L
            val remainingMs = duration - position

            if (skipOutroMs > 0 && remainingMs <= skipOutroMs) {
                // Outro trigger!
                Log.d("PlayerService", "Skip Outro Triggered. remainingMs=$remainingMs, skipOutroMs=$skipOutroMs")
                if (player.hasNextMediaItem()) {
                    // We transition next immediately
                    player.seekToNextMediaItem()
                } else {
                    // Last chapter, pause and finish
                    player.pause()
                    player.seekTo(duration)
                }
            }
        }

        // Auto Save progress every 4 seconds dynamically or whenever large chunk is played
        if (System.currentTimeMillis() % 4000 < 500) {
            saveHistory(currentIdx, position)
        }

        updateUiState()
    }

    private fun saveHistory(trackIndex: Int, positionMs: Long) {
        val book = currentBook ?: return
        serviceScope.launch {
            repository.savePlaybackProgress(book.id, trackIndex, positionMs)
            // Update cached object in memory
            currentBook = repository.getBookById(book.id)
        }
    }

    private fun updateUiState() {
        val book = currentBook
        val currentIdx = player.currentMediaItemIndex
        val track = if (book != null && currentIdx >= 0 && currentIdx < currentTracks.size) currentTracks[currentIdx] else null

        _serviceState.value = PlayerState(
            bookId = book?.id,
            bookTitle = book?.title ?: "",
            coverColorHex = book?.coverColorHex ?: "#FF6200EE",
            currentTrackIndex = currentIdx,
            currentTrackTitle = track?.title ?: "未在播放",
            currentPositionMs = player.currentPosition,
            durationMs = player.duration.coerceAtLeast(0L),
            isPlaying = player.isPlaying,
            playbackSpeed = player.playbackParameters.speed,
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.hasPreviousMediaItem(),
            skipIntroSeconds = book?.skipIntroSeconds ?: -1,
            skipOutroSeconds = book?.skipOutroSeconds ?: -1,
            globalIntroSeconds = globalIntroSec,
            globalOutroSeconds = globalOutroSec
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("PlayerService", "onStartCommand called")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        Log.d("PlayerService", "onDestroy called")
        stopPollingProgress()
        serviceScope.cancel()
        
        // Release Media3
        mediaSession?.run {
            release()
            mediaSession = null
        }
        player.release()
        super.onDestroy()
    }
}
