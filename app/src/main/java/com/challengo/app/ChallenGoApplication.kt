package com.challengo.app.ui.viewmodel

import android.app.Application

class ChallenGoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
    }
}