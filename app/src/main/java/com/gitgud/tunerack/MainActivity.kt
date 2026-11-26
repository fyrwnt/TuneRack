package com.gitgud.tunerack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import com.gitgud.tunerack.playback.LocalPlayerConnection
import com.gitgud.tunerack.playback.PlayerConnection
import com.gitgud.tunerack.ui.TuneRackApp
import com.gitgud.tunerack.ui.theme.TuneRackTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val playerConnection by lazy { PlayerConnection(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalPlayerConnection provides playerConnection) {
                TuneRackTheme { TuneRackApp() }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { playerConnection.connect() }
    }

    override fun onStop() {
        playerConnection.release()
        super.onStop()
    }
}
