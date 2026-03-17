package com.challengo.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.challengo.app.notifications.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class StreakWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val userDoc = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
        val streak = userDoc.getLong("currentStreak")?.toInt() ?: 0

        if (streak >= 3) {
            NotificationHelper.show(
                applicationContext,
                1003,
                "3-Day Streak!",
                "Amazing consistency. Keep your challenge streak alive."
            )
        }

        return Result.success()
    }
}
