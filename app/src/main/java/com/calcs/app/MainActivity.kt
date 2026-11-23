package com.calcs.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.calcs.app.ui.AppState
import com.calcs.app.ui.CalcsApp

class MainActivity : ComponentActivity() {
    private lateinit var app: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = AppState(applicationContext)
        setContent { CalcsApp(app) }
        if (savedInstanceState == null) openBackup(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openBackup(intent)
    }

    /** A .calcs file opened from outside the app: load it (the open project goes to Old projects). */
    private fun openBackup(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val text = try { contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } } catch (_: Exception) { null }
        val name = text?.let { app.importBackup(it) }
        Toast.makeText(this, if (name != null) "Opened “$name”. The previous project is in Old projects." else "This file is not a Calcs backup.", Toast.LENGTH_LONG).show()
        app.screen = com.calcs.app.ui.Screen.Home
    }

    override fun onPause() {
        super.onPause()
        app.saveNow()
    }
}
