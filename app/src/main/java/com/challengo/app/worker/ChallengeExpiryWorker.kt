package com.challengo.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.challengo.app.data.repository.NotificationRepository
import com.challengo.app.notifications.NotificationHelper
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ChallengeExpiryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val uid = inputData.getString("uid").orEmpty()
        val challengeId = inputData.getString("challengeId").orEmpty()
        val thresholdHours = inputData.getInt("thresholdHours", 0)
        if (uid.isBlank() || challengeId.isBlank() || thresholdHours <= 0) {
            return Result.success()
        }
        val firestore = FirebaseFirestore.getInstance()
        val userDoc = firestore.collection("users").document(uid).get().await()
        val activeChallengeId = userDoc.getString("activeChallengeId").orEmpty()
        val activeChallengeCompleted = userDoc.getBoolean("activeChallengeCompleted") ?: false
        val rolledAt = userDoc.getLong("activeChallengeRolledAt")
            ?: userDoc.getTimestamp("activeChallengeRolledAt")?.toDate()?.time
            ?: 0L
        val now = System.currentTimeMillis()
        val withinWindow = rolledAt > 0L && now - rolledAt <= 24L * 60L * 60L * 1000L
        if (activeChallengeId != challengeId || activeChallengeCompleted || !withinWindow) {
            return Result.success()
        }
        val notificationRepository = NotificationRepository(firestore)
        notificationRepository.createChallengeNotDoneReminderNotification(
            targetUid = uid,
            challengeId = challengeId
        )
        NotificationHelper.show(
            applicationContext,
            ("$uid|$challengeId|$thresholdHours").hashCode(),
            NotificationRepository.TITLE_DAILY_CHALLENGE_REMINDER,
            NotificationRepository.BODY_DAILY_CHALLENGE_NOT_DONE,
            destination = NotificationRepository.DESTINATION_ROLL_CHALLENGE
        )
        return Result.success()
    }
}