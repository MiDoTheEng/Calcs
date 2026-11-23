package com.calcs.app

import android.os.Bundle
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
    }

    override fun onPause() {
        super.onPause()
        app.saveNow()
    }
}
