package com.challengo.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.challengo.app.notifications.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class SocialActivityWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val prefs = applicationContext.getSharedPreferences("challengo_social", Context.MODE_PRIVATE)
        val lastTotal = prefs.getLong("last_interactions_total", 0L)

        val posts = FirebaseFirestore.getInstance()
            .collection("posts")
            .whereEqualTo("userId", uid)
            .get()
            .await()

        val totalInteractions = posts.documents.sumOf { doc ->
            (doc.getLong("likeCount") ?: doc.getLong("likesCount") ?: 0L) +
                (doc.getLong("commentsCount") ?: 0L)
        }

        if (totalInteractions > lastTotal) {
            NotificationHelper.show(
                applicationContext,
                1004,
                "New activity on your posts",
                "You have new likes or comments waiting."
            )
        }

        prefs.edit().putLong("last_interactions_total", totalInteractions).apply()
        return Result.success()
    }
}
