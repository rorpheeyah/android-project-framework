package com.compass.app

import android.app.Application
import com.aos.core.logging.Logger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CompassApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Logger.installDebug()
    }
}
