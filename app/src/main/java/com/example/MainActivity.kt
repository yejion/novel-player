package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.data.AudiobookDatabase
import com.example.data.AudiobookRepository
import com.example.player.AudiobookPlayerService
import com.example.ui.AudiobookAppContent
import com.example.ui.MainViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    // Initialize Database Components
    private val database by lazy { AudiobookDatabase.getInstance(applicationContext) }
    private val repository by lazy { AudiobookRepository(database) }
    
    // Instantiate Main ViewModel
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(repository)
    }

    private var playerService: AudiobookPlayerService? = null
    private var isBound = false

    // ServiceConnection handles active background binding
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("MainActivity", "Service bounded successfully")
            val binder = service as? AudiobookPlayerService.LocalBinder
            playerService = binder?.getService()
            playerService?.let {
                viewModel.onServiceConnected(it)
            }
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("MainActivity", "Service unbound or disconnected")
            playerService = null
            viewModel.onServiceDisconnected()
            isBound = false
        }
    }

    // Modern multi permission requester
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[android.Manifest.permission.READ_MEDIA_AUDIO] ?: false
        val selectGranted = permissions[android.Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        val notifyGranted = permissions[android.Manifest.permission.POST_NOTIFICATIONS] ?: false
        Log.d("MainActivity", "Permission feedback: audioIcon=$audioGranted, notifyIcon=$notifyGranted, storageIcon=$selectGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Dynamic Edge-to-Edge immersion mapping
        enableEdgeToEdge()
        
        // Ask for permissions
        checkAndRequestPermissions()

        // Start Foreground Service initially so it remains active in the background
        startPlayerService()

        setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                AudiobookAppContent(viewModel = viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Connect and bind player service
        val intent = Intent(this, AudiobookPlayerService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            viewModel.onServiceDisconnected()
            isBound = false
        }
    }

    private fun startPlayerService() {
        val intent = Intent(this, AudiobookPlayerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun checkAndRequestPermissions() {
        val list = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                list.add(android.Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                list.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                list.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (list.isNotEmpty()) {
            permissionLauncher.launch(list.toTypedArray())
        }
    }
}
