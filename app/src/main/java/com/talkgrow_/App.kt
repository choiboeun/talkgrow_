// app/src/main/java/com/talkgrow_/App.kt
package com.talkgrow_

import android.app.Application
import android.util.Log

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("AppInit", "Application created")
    }
}
