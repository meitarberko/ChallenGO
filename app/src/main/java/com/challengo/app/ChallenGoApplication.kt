package com.challengo.app

import android.app.Application
import com.challengo.app.notifications.NotificationHelper

class ChallenGoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
    }
}
