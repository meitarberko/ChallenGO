package com.challengo.app.data.repository

import android.util.Log
import com.challengo.app.data.local.dao.ChallengeDao
import com.challengo.app.data.model.Challenge
import com.challengo.app.data.model.DailyChallenge
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

class ChallengeRepository(
    private val firestore: FirebaseFirestore,
    private val challengeDao: ChallengeDao
) {
    companion object {
        private const val TAG = "ChallengeRepository"
        private const val COLLECTION_CHALLENGES = "challenges"
        private const val NO_CHALLENGES_MESSAGE = "No challenges available. Please try again later."
        private const val DEBUG_SHORT_CHALLENGE_WINDOW = false
    }

    private val completionMutex = Mutex()

    suspend fun fetchRandomChallenge(): Result<Challenge> {
        return try {
            Log.d(
                TAG,
                "event=fetch_random_challenge_start authUid=${currentAuthUid() ?: "null"} path=$COLLECTION_CHALLENGES query=active:true"
            )
            val snapshot = firestore.collection(COLLECTION_CHALLENGES)
                .whereEqualTo("active", true)
                .get()
                .await()

            Log.d(
                TAG,
                "event=fetch_random_challenge_success authUid=${currentAuthUid() ?: "null"} path=$COLLECTION_CHALLENGES resultCount=${snapshot.size()}"
            )

            val challenges = snapshot.documents.mapNotNull { doc ->
                val name = doc.getString("name")?.trim().orEmpty()
                val description = doc.getString("description")?.trim().orEmpty()
                if (name.isEmpty() && description.isEmpty()) {
                    null
                } else {
                    val difficultyRaw = doc.get("difficulty")
                    val difficulty = parseDifficulty(difficultyRaw)
                    val points = doc.getLong("points")?.toInt() ?: 10
                    val hashtag = normalizeHashtag(doc.getString("hashtag") ?: "")

                    Challenge(
                        id = doc.id,
                        text = name,
                        category = description,
                        difficulty = difficulty,
                        hashtag = hashtag,
                        points = points
                    )
                }
            }

            if (challenges.isEmpty()) {
                Log.w(
                    TAG,
                    "event=fetch_random_challenge_empty authUid=${currentAuthUid() ?: "null"} path=$COLLECTION_CHALLENGES reason=no_matching_docs_or_schema"
                )
                Result.failure(Exception(NO_CHALLENGES_MESSAGE))
            } else {
                Result.success(challenges.random())
            }
        } catch (e: Exception) {
            Log.e(
                TAG,
                "event=fetch_random_challenge_fail authUid=${currentAuthUid() ?: "null"} path=$COLLECTION_CHALLENGES query=active:true message=${e.message}",
                e
            )
            Result.failure(Exception(NO_CHALLENGES_MESSAGE))
        }
    }

    @Deprecated("Use fetchRandomChallenge()")
    suspend fun getAllChallenges(): List<Challenge> {
        return try {
            val snapshot = firestore.collection(COLLECTION_CHALLENGES).get().await()
            snapshot.documents.map { doc ->
                val difficulty = parseDifficulty(doc.get("difficulty"))
                Challenge(
                    id = doc.id,
                    text = doc.getString("text") ?: doc.getString("name") ?: "",
                    category = doc.getString("category") ?: doc.getString("description") ?: "",
                    difficulty = difficulty,
                    hashtag = normalizeHashtag(doc.getString("hashtag") ?: ""),
                    points = doc.getLong("points")?.toInt() ?: 10
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Deprecated("Use fetchRandomChallenge()")
    suspend fun getRandomChallenge(): Challenge? {
        val challenges = getAllChallenges()
        return if (challenges.isNotEmpty()) challenges.random() else null
    }

    suspend fun rollDailyChallenge(userId: String): Result<DailyChallenge> {
        return try {
            Log.d(
                TAG,
                "event=roll_daily_challenge_start authUid=${currentAuthUid() ?: "null"} userId=$userId userPath=users/$userId"
            )
            val existing = challengeDao.getDailyChallengeSync(userId)
            if (existing != null && !existing.isExpired()) {
                return Result.success(existing)
            }

            if (existing != null && existing.isExpired()) {
                challengeDao.deleteDailyChallenge(userId)
            }

            val challengeResult = fetchRandomChallenge()
            val challenge = challengeResult.getOrNull()
                ?: return Result.failure(challengeResult.exceptionOrNull() ?: Exception(NO_CHALLENGES_MESSAGE))
            val now = System.currentTimeMillis()
            val expiresAt = now + challengeDurationMillis()

            val dailyChallenge = DailyChallenge(
                userId = userId,
                challengeId = challenge.id,
                challengeText = challenge.text,
                challengeCategory = challenge.category,
                challengeDifficulty = challenge.difficulty,
                challengeHashtag = normalizeHashtag(challenge.hashtag),
                challengePoints = challenge.points,
                rollTime = now,
                expiresAt = expiresAt,
                pointsAwarded = false,
                completedAt = null
            )

            challengeDao.insertDailyChallenge(dailyChallenge)
            firestore.collection("users").document(userId)
                .set(
                    mapOf(
                        "activeChallengeId" to dailyChallenge.challengeId,
                        "activeChallengeHashtag" to dailyChallenge.challengeHashtag,
                        "activeChallengeRolledAt" to dailyChallenge.rollTime,
                        "activeChallengeCompleted" to false,
                        "lastRollAt" to dailyChallenge.rollTime
                    ),
                    SetOptions.merge()
                )
                .await()
            Log.d(
                TAG,
                "event=roll_daily_challenge_success authUid=${currentAuthUid() ?: "null"} userId=$userId challengeId=${dailyChallenge.challengeId} userPath=users/$userId"
            )
            Result.success(dailyChallenge)
        } catch (e: Exception) {
            Log.e(
                TAG,
                "event=roll_daily_challenge_fail authUid=${currentAuthUid() ?: "null"} userId=$userId userPath=users/$userId message=${e.message}",
                e
            )
            Result.failure(e)
        }
    }

    fun getDailyChallenge(userId: String): Flow<DailyChallenge?> {
        return challengeDao.getDailyChallenge(userId)
    }

    suspend fun getDailyChallengeSync(userId: String): DailyChallenge? {
        return challengeDao.getDailyChallengeSync(userId)
    }

    suspend fun completeChallengeAndAward(userId: String): Result<CompleteChallengeResult> {
        return completionMutex.withLock {
            try {
                val dailyChallenge = challengeDao.getDailyChallengeSync(userId)
                    ?: return@withLock Result.success(
                        CompleteChallengeResult(
                            pointsAwarded = 0,
                            challenge = null
                        )
                    )

                if (dailyChallenge.isExpired()) {
                    return@withLock Result.success(
                        CompleteChallengeResult(
                            pointsAwarded = 0,
                            challenge = dailyChallenge
                        )
                    )
                }
                if (dailyChallenge.pointsAwarded) {
                    return@withLock Result.success(
                        CompleteChallengeResult(
                            pointsAwarded = 0,
                            challenge = dailyChallenge
                        )
                    )
                }

                val userRef = firestore.collection("users").document(userId)
                val now = System.currentTimeMillis()
                val challengeWindowMillis = challengeDurationMillis()

                val awarded = firestore.runTransaction { transaction ->
                    val userSnap = transaction.get(userRef)
                    if (!userSnap.exists()) {
                        return@runTransaction false
                    }
                    val activeChallengeId = userSnap.getString("activeChallengeId")?.trim().orEmpty()
                    val activeChallengeRolledAt = userSnap.getLong("activeChallengeRolledAt")
                        ?: (userSnap.getTimestamp("activeChallengeRolledAt")?.toDate()?.time ?: 0L)
                    val activeChallengeCompleted = userSnap.getBoolean("activeChallengeCompleted") ?: false
                    @Suppress("UNCHECKED_CAST")
                    val completedIds = (userSnap.get("completedChallengeIds") as? List<String>) ?: emptyList()

                    val validActiveChallenge = activeChallengeId == dailyChallenge.challengeId
                    val withinWindow = activeChallengeRolledAt > 0L && (now - activeChallengeRolledAt) <= challengeWindowMillis
                    val alreadyCompleted = activeChallengeCompleted || completedIds.contains(dailyChallenge.challengeId)

                    if (!validActiveChallenge || !withinWindow || alreadyCompleted) {
                        return@runTransaction false
                    }

                    val currentPoints = (userSnap.getLong("points")
                        ?: userSnap.getLong("totalPoints")
                        ?: 0L).toInt()
                    val newPoints = currentPoints + 10
                    val newLevel = (newPoints / 50) + 1

                    transaction.update(
                        userRef,
                        mapOf(
                            "points" to newPoints,
                            "totalPoints" to newPoints,
                            "level" to newLevel,
                            "hashtags" to FieldValue.arrayUnion(dailyChallenge.challengeHashtag),
                            "completedChallengeIds" to FieldValue.arrayUnion(dailyChallenge.challengeId),
                            "activeChallengeCompleted" to true,
                            "lastChallengeCompletedAt" to now,
                            "challengesCompleted" to FieldValue.increment(1)
                        )
                    )
                    true
                }.await()

                val completedChallenge = if (awarded) {
                    dailyChallenge.copy(
                        pointsAwarded = true,
                        completedAt = now
                    )
                } else {
                    dailyChallenge
                }
                if (awarded) {
                    challengeDao.insertDailyChallenge(completedChallenge)
                }

                Result.success(
                    CompleteChallengeResult(
                        pointsAwarded = if (awarded) 10 else 0,
                        challenge = completedChallenge
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun deleteDailyChallenge(userId: String) {
        challengeDao.deleteDailyChallenge(userId)
        firestore.collection("users").document(userId).set(
            mapOf(
                "activeChallengeId" to null,
                "activeChallengeHashtag" to null,
                "activeChallengeRolledAt" to null,
                "activeChallengeCompleted" to false
            ),
            SetOptions.merge()
        ).await()
    }

    private fun normalizeHashtag(value: String): String {
        return value.trim().removePrefix("#")
    }

    private fun parseDifficulty(raw: Any?): Int {
        return when (raw) {
            is Long -> raw.toInt()
            is Int -> raw
            is String -> when (raw.trim().lowercase()) {
                "easy" -> 1
                "medium" -> 2
                "hard" -> 3
                else -> 1
            }
            else -> 1
        }
    }

    private fun challengeDurationMillis(): Long {
        return if (DEBUG_SHORT_CHALLENGE_WINDOW) {
            60_000L
        } else {
            24 * 60 * 60 * 1000L
        }
    }

    private fun pointsForDifficulty(difficulty: Int): Int {
        return when (difficulty) {
            1 -> 10
            2 -> 20
            3 -> 30
            else -> 10
        }
    }

    private fun currentAuthUid(): String? = FirebaseAuth.getInstance().currentUser?.uid
}

data class CompleteChallengeResult(
    val pointsAwarded: Int,
    val challenge: DailyChallenge?
)
