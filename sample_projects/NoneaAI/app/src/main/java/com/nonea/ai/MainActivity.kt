package com.nonea.ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nonea.ai.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: ChatViewModel = viewModel()
            val s by vm.settings.collectAsState()

            // Voice only works while Nonea is on screen: leaving the app stops listening, speaking and hands-free.
            // Coming back re-reads the mic permission (the user may have changed it in system Settings).
            DisposableEffect(Unit) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> {
                            vm.setMicGranted(ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                            vm.onForeground()
                        }
                        Lifecycle.Event.ON_STOP -> vm.onBackground()
                        else -> {}
                    }
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }

            NoneaTheme(s.theme, s.uiLang) {
                var settings by remember { mutableStateOf(false) }
                var automation by remember { mutableStateOf(false) }
                BackHandler(automation) { automation = false }
                BackHandler(settings && !automation) { settings = false }
                when {
                    automation -> AutomationScreen(vm) { automation = false }
                    settings -> SettingsScreen(vm, onBack = { settings = false }, onAutomation = { automation = true })
                    else -> ChatScreen(vm, onSettings = { settings = true }, onAutomation = { automation = true })
                }
                AutomationConfirmDialog(vm)   // above every screen
            }
        }
    }
}
