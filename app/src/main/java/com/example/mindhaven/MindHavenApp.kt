package com.example.mindhaven

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MindHavenApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}