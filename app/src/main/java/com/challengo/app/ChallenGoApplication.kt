package com.challengo.app

import android.app.Application

class ChallenGoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
    }
}