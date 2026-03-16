package com.challengo.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.challengo.app.data.repository.NotificationRepository
import com.challengo.app.notifications.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class InactiveReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val firestore = FirebaseFirestore.getInstance()
        val userDoc = firestore.collection("users").document(uid).get().await()
        val activeChallengeId = userDoc.getString("activeChallengeId").orEmpty()
        val lastRollAt = userDoc.getLong("lastRollAt")
            ?: userDoc.getLong("activeChallengeRolledAt")
            ?: userDoc.getTimestamp("activeChallengeRolledAt")?.toDate()?.time
            ?: 0L
        val now = System.currentTimeMillis()
        val oneDayMillis = 24L * 60L * 60L * 1000L

        if (activeChallengeId.isNotBlank() || lastRollAt <= 0L || now - lastRollAt < oneDayMillis) {
            return Result.success()
        }

        val reminderKey = "${uid}_${(now - lastRollAt) / oneDayMillis}"
        val prefs = applicationContext.getSharedPreferences("challengo_notifications", Context.MODE_PRIVATE)
        val lastKey = prefs.getString("no_roll_reminder_key", "")
        if (lastKey == reminderKey) {
            return Result.success()
        }
        prefs.edit().putString("no_roll_reminder_key", reminderKey).apply()
        NotificationRepository(firestore).createNoRollReminderNotification(uid, reminderKey)
        NotificationHelper.show(
            applicationContext,
            reminderKey.hashCode(),
            NotificationRepository.TITLE_DAILY_CHALLENGE,
            NotificationRepository.BODY_DAILY_CHALLENGE_NO_ROLL,
            destination = NotificationRepository.DESTINATION_ROLL_CHALLENGE
        )
        return Result.success()
    }
}