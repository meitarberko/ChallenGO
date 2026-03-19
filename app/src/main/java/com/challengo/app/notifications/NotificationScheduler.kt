package com.challengo.app.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.challengo.app.worker.ChallengeExpiryWorker
import com.challengo.app.worker.InactiveReminderWorker
import com.challengo.app.worker.SocialActivityWorker
import com.challengo.app.worker.StreakWorker
import java.util.concurrent.TimeUnit

object NotificationScheduler {
    private const val TWELVE_HOURS_MILLIS = 12 * 60 * 60 * 1000L
    private const val THREE_HOURS_BEFORE_END_MILLIS = 21 * 60 * 60 * 1000L

    fun scheduleRecurring(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inactiveWork = PeriodicWorkRequestBuilder<InactiveReminderWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "no_roll_24h_reminder",
            ExistingPeriodicWorkPolicy.UPDATE,
            inactiveWork
        )

        val streakWork = PeriodicWorkRequestBuilder<StreakWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "streak_reminder",
            ExistingPeriodicWorkPolicy.UPDATE,
            streakWork
        )

        val socialWork = PeriodicWorkRequestBuilder<SocialActivityWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "social_activity_poll",
            ExistingPeriodicWorkPolicy.UPDATE,
            socialWork
        )
    }

    fun scheduleChallengeNotDoneReminders(
        context: Context,
        uid: String,
        challengeId: String,
        rollTime: Long
    ) {
        val now = System.currentTimeMillis()
        val reminder12At = rollTime + TWELVE_HOURS_MILLIS
        val reminder3At = rollTime + THREE_HOURS_BEFORE_END_MILLIS
        scheduleChallengeReminderThreshold(context, uid, challengeId, 12, reminder12At - now)
        scheduleChallengeReminderThreshold(context, uid, challengeId, 3, reminder3At - now)
    }

    fun cancelChallengeNotDoneReminders(context: Context, uid: String, challengeId: String?) {
        if (uid.isBlank() || challengeId.isNullOrBlank()) return
        WorkManager.getInstance(context).cancelUniqueWork(workName(uid, challengeId, 12))
        WorkManager.getInstance(context).cancelUniqueWork(workName(uid, challengeId, 3))
    }

    private fun scheduleChallengeReminderThreshold(
        context: Context,
        uid: String,
        challengeId: String,
        thresholdHours: Int,
        delayMillis: Long
    ) {
        if (uid.isBlank() || challengeId.isBlank() || delayMillis <= 0L) return
        val request = OneTimeWorkRequestBuilder<ChallengeExpiryWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    "uid" to uid,
                    "challengeId" to challengeId,
                    "thresholdHours" to thresholdHours
                )
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(uid, challengeId, thresholdHours),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun workName(uid: String, challengeId: String, thresholdHours: Int): String {
        return "challenge_not_done_${uid}_${challengeId}_${thresholdHours}h"
    }
}
