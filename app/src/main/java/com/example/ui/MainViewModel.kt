package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.player.AudiobookPlayerService
import com.example.player.PlayerState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(private val repository: AudiobookRepository) : ViewModel() {

    // 1. Database observations
    val allBooksWithTracks: StateFlow<List<BookWithTracks>> = repository.allBooksWithTracks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val globalSettings: StateFlow<GlobalSettings> = repository.globalSettings
        .map { it ?: GlobalSettings() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = GlobalSettings()
        )

    // 2. Bound Service interaction
    private val _boundService = MutableStateFlow<AudiobookPlayerService?>(null)
    val boundService = _boundService.asStateFlow()

    // Expose the play state dynamically. If bound, observe its state; otherwise fallback to default empty state.
    val playerState: StateFlow<PlayerState> = _boundService
        .flatMapLatest { service ->
            service?.serviceState ?: flowOf(PlayerState())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PlayerState()
        )

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    // 3. Service lifecycle hooks called from Activity
    fun onServiceConnected(service: AudiobookPlayerService) {
        _boundService.value = service
    }

    fun onServiceDisconnected() {
        _boundService.value = null
    }

    // 4. UI Actions - Playback controls (forwarded to service)
    fun playBook(book: Book, tracks: List<Track>, startingIndex: Int = -1) {
        _boundService.value?.playBook(book, tracks, startingIndex)
    }

    fun togglePlayPause() {
        _boundService.value?.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        _boundService.value?.seekTo(positionMs)
    }

    fun seekForward() {
        _boundService.value?.seekForward()
    }

    fun seekBackward() {
        _boundService.value?.seekBackward()
    }

    fun setPlaybackSpeed(speed: Float) {
        _boundService.value?.changePlaybackSpeed(speed)
    }

    fun playNext() {
        _boundService.value?.playNext()
    }

    fun playPrevious() {
        _boundService.value?.playPrevious()
    }

    // 5. UI Actions - Setup and settings modifications
    fun scanLocalFolders(context: Context, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            _isScanning.value = true
            val count = repository.scanLocalDirectories(context)
            _isScanning.value = false
            onComplete(count)
        }
    }

    fun seedDemoBooks(context: Context, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isScanning.value = true
            repository.seedDemoBooks(context)
            _isScanning.value = false
            onComplete()
        }
    }

    fun deleteBook(bookId: Long) {
        viewModelScope.launch {
            // If deleting the active book, stop playing
            val currentState = playerState.value
            if (currentState.bookId == bookId) {
                _boundService.value?.player?.stop()
                _boundService.value?.player?.clearMediaItems()
            }
            repository.deleteBook(bookId)
        }
    }

    fun updateBookSkipIntro(bookId: Long, seconds: Int) {
        viewModelScope.launch {
            repository.updateBookSkipIntro(bookId, seconds)
        }
    }

    fun updateBookSkipOutro(bookId: Long, seconds: Int) {
        viewModelScope.launch {
            repository.updateBookSkipOutro(bookId, seconds)
        }
    }

    fun updateGlobalSettings(introSec: Int, outroSec: Int) {
        viewModelScope.launch {
            repository.updateGlobalSettings(
                GlobalSettings(
                    id = 1,
                    defaultSkipIntroSeconds = introSec,
                    defaultSkipOutroSeconds = outroSec
                )
            )
        }
    }

    class Factory(private val repository: AudiobookRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
