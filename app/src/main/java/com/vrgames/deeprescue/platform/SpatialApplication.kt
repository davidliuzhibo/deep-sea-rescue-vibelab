package com.vrgames.deeprescue.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.vrgames.deeprescue.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.vrgames.deeprescue.game.AutoPilot.register(this)
        launch(::mainApp)
    }
}
