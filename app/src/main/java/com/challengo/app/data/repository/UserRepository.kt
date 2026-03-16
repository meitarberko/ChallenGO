package com.challengo.app.data.repository

import com.challengo.app.data.local.dao.UserDao
import com.challengo.app.data.model.User
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class UserRepository(
    private val firestore: FirebaseFirestore,
    private val userDao: UserDao
) {
    companion object {
        const val ERROR_USERNAME_TAKEN = "error_username_taken"
        const val ERROR_USERNAME_INVALID = "error_username_invalid"

        fun normalizeUsername(username: String): String = username.trim()

        fun normalizeUsernameLower(username: String): String = normalizeUsername(username).lowercase()

        fun isUsernameFormatValid(username: String): Boolean = normalizeUsername(username).isNotEmpty()
    }

    suspend fun updateUserProfile(
        uid: String,
        username: String?,
        profileImageUri: String?,
        clearProfileImage: Boolean
    ): Result<User> {
        return try {
            val existingUser = userDao.getUserSync(uid) ?: return Result.failure(Exception("User not found"))
            var updatedUser = existingUser
            var changed = false

            val normalizedUsername = username?.let { normalizeUsername(it) }
            if (normalizedUsername != null) {
                if (!isUsernameFormatValid(normalizedUsername)) {
                    return Result.failure(Exception(ERROR_USERNAME_INVALID))
                }
                val usernameResult = updateUsername(uid, normalizedUsername)
                if (usernameResult.isFailure) {
                    return Result.failure(usernameResult.exceptionOrNull() ?: Exception("Failed to update username"))
                }
                val didUsernameChange = usernameResult.getOrNull() == true
                if (didUsernameChange) {
                    updatedUser = updatedUser.copy(username = normalizedUsername)
                    changed = true
                }
            }

            if (clearProfileImage) {
                firestore.collection("users").document(uid)
                    .update("profileImageUri", null)
                    .await()
                updatedUser = updatedUser.copy(profileImageUri = null)
                changed = true
            } else if (profileImageUri != null) {
                firestore.collection("users").document(uid)
                    .update("profileImageUri", profileImageUri)
                    .await()
                updatedUser = updatedUser.copy(profileImageUri = profileImageUri)
                changed = true
            }

            if (!changed) {
                return Result.success(existingUser)
            }

            userDao.updateUser(updatedUser)
            Result.success(updatedUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncUserFromFirestore(uid: String): Result<User> {
        return try {
            val userDoc = firestore.collection("users").document(uid).get().await()
            if (!userDoc.exists()) {
                return Result.failure(Exception("User not found"))
            }
            val user = User(
                uid = userDoc.getString("uid") ?: uid,
                username = userDoc.getString("username") ?: "",
                email = userDoc.getString("email") ?: "",
                firstName = userDoc.getString("firstName") ?: "",
                lastName = userDoc.getString("lastName") ?: "",
                age = userDoc.getLong("age")?.toInt() ?: 0,
                profileImageUri = userDoc.getString("profileImageUri") ?: userDoc.getString("profileImageUrl"),
                totalPoints = (userDoc.getLong("totalPoints") ?: userDoc.getLong("points") ?: 0L).toInt(),
                challengesCompleted = userDoc.getLong("challengesCompleted")?.toInt() ?: 0,
                level = userDoc.getLong("level")?.toInt()
                    ?: ((((userDoc.getLong("totalPoints") ?: userDoc.getLong("points") ?: 0L).toInt()) / 50) + 1),
                lastChallengeCompletedAt = userDoc.getLong("lastChallengeCompletedAt"),
                currentStreak = userDoc.getLong("currentStreak")?.toInt() ?: 0
            )
            userDao.insertUser(user)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUsername(uid: String, newUsernameInput: String): Result<Boolean> {
        return try {
            val newUsername = normalizeUsername(newUsernameInput)
            if (!isUsernameFormatValid(newUsername)) {
                return Result.failure(Exception(ERROR_USERNAME_INVALID))
            }
            val newUsernameLower = normalizeUsernameLower(newUsername)
            val usersRef = firestore.collection("users").document(uid)

            val didChange = firestore.runTransaction { transaction ->
                val userSnap = transaction.get(usersRef)
                if (!userSnap.exists()) {
                    throw Exception("User not found")
                }

                val oldUsernameLower = userSnap.getString("usernameLower")
                    ?.trim()
                    ?.lowercase()
                    ?.ifEmpty { null }
                    ?: userSnap.getString("username")
                        ?.trim()
                        ?.lowercase()
                        ?.ifEmpty { null }

                if (oldUsernameLower == newUsernameLower) {
                    return@runTransaction false
                }

                val newUsernameRef = firestore.collection("usernames").document(newUsernameLower)
                val newUsernameSnap = transaction.get(newUsernameRef)
                val ownerUid = newUsernameSnap.getString("uid")
                if (newUsernameSnap.exists() && ownerUid != null && ownerUid != uid) {
                    throw UsernameTakenForUpdateException()
                }

                val oldUsernameRef = oldUsernameLower?.let {
                    firestore.collection("usernames").document(it)
                }
                val oldUsernameSnap = oldUsernameRef?.let { transaction.get(it) }

                transaction.set(
                    newUsernameRef,
                    hashMapOf(
                        "uid" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                transaction.update(
                    usersRef,
                    mapOf(
                        "username" to newUsername,
                        "usernameLower" to newUsernameLower
                    )
                )

                if (oldUsernameRef != null && oldUsernameSnap != null) {
                    if (oldUsernameSnap.exists() && oldUsernameSnap.getString("uid") == uid) {
                        transaction.delete(oldUsernameRef)
                    }
                }

                true
            }.await()

            val existingUser = userDao.getUserSync(uid)
            if (existingUser != null && didChange) {
                userDao.updateUser(existingUser.copy(username = newUsername))
            }

            Result.success(didChange)
        } catch (e: Exception) {
            Result.failure(mapUsernameUpdateError(e))
        }
    }

    suspend fun updateUserPoints(uid: String, pointsToAdd: Int): Result<User> {
        return try {
            val existingUser = userDao.getUserSync(uid) ?: return Result.failure(Exception("User not found"))
            val now = System.currentTimeMillis()
            val newTotalPoints = existingUser.totalPoints + pointsToAdd
            val newChallengesCompleted = existingUser.challengesCompleted + 1
            val newLevel = (newTotalPoints / 50) + 1
            val newStreak = calculateStreak(existingUser.lastChallengeCompletedAt, now, existingUser.currentStreak)

            val updates = hashMapOf<String, Any>(
                "totalPoints" to newTotalPoints,
                "challengesCompleted" to newChallengesCompleted,
                "level" to newLevel,
                "lastChallengeCompletedAt" to now,
                "currentStreak" to newStreak
            )

            firestore.collection("users").document(uid).update(updates).await()

            val updatedUser = existingUser.copy(
                totalPoints = newTotalPoints,
                challengesCompleted = newChallengesCompleted,
                level = newLevel,
                lastChallengeCompletedAt = now,
                currentStreak = newStreak
            )
            userDao.updateUser(updatedUser)
            Result.success(updatedUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun awardChallenge(uid: String, hashtag: String): Result<User> {
        return try {
            val normalizedHashtag = hashtag.trim().removePrefix("#")
            val existingUser = userDao.getUserSync(uid) ?: return Result.failure(Exception("User not found"))
            val now = System.currentTimeMillis()
            val newTotalPoints = existingUser.totalPoints + 10
            val newChallengesCompleted = existingUser.challengesCompleted + 1
            val newLevel = (newTotalPoints / 50) + 1
            val newStreak = calculateStreak(existingUser.lastChallengeCompletedAt, now, existingUser.currentStreak)

            firestore.collection("users").document(uid).update(
                mapOf(
                    "points" to FieldValue.increment(10),
                    "totalPoints" to FieldValue.increment(10),
                    "hashtags" to FieldValue.arrayUnion(normalizedHashtag),
                    "challengesCompleted" to newChallengesCompleted,
                    "level" to newLevel,
                    "lastChallengeCompletedAt" to now,
                    "currentStreak" to newStreak
                )
            ).await()

            val updatedUser = existingUser.copy(
                totalPoints = newTotalPoints,
                challengesCompleted = newChallengesCompleted,
                level = newLevel,
                lastChallengeCompletedAt = now,
                currentStreak = newStreak
            )
            userDao.updateUser(updatedUser)
            Result.success(updatedUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserProfileStats(uid: String): Result<UserProfileStats> {
        return try {
            val snapshot = firestore.collection("users").document(uid).get().await()
            if (!snapshot.exists()) {
                return Result.failure(Exception("User not found"))
            }

            val points = (snapshot.getLong("points")
                ?: snapshot.getLong("totalPoints")
                ?: 0L).toInt()

            @Suppress("UNCHECKED_CAST")
            val hashtags = (snapshot.get("hashtags") as? List<String>)
                ?.map { it.trim().removePrefix("#") }
                ?.filter { it.isNotEmpty() }
                ?.distinct()
                ?: emptyList()

            Result.success(UserProfileStats(points = points, hashtags = hashtags))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getUser(uid: String): Flow<User?> {
        return userDao.getUser(uid)
    }

    suspend fun getUserSync(uid: String): User? {
        return userDao.getUserSync(uid)
    }

    private fun calculateStreak(lastCompletedAt: Long?, now: Long, currentStreak: Int): Int {
        if (lastCompletedAt == null) {
            return 1
        }

        val lastDate = Instant.ofEpochMilli(lastCompletedAt).atZone(ZoneOffset.UTC).toLocalDate()
        val nowDate = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate()
        val daysBetween = ChronoUnit.DAYS.between(lastDate, nowDate)

        return when {
            daysBetween <= 0 -> currentStreak
            daysBetween == 1L -> currentStreak + 1
            else -> 1
        }
    }

    private fun mapUsernameUpdateError(error: Exception): Exception {
        return when (error) {
            is UsernameTakenForUpdateException -> Exception(ERROR_USERNAME_TAKEN)
            else -> {
                val causeTaken = generateSequence(error as Throwable?) { it.cause }
                    .any { it is UsernameTakenForUpdateException || it.message?.contains("USERNAME_TAKEN") == true }
                if (causeTaken) Exception(ERROR_USERNAME_TAKEN) else error
            }
        }
    }
}

private class UsernameTakenForUpdateException : RuntimeException("USERNAME_TAKEN")

data class UserProfileStats(
    val points: Int,
    val hashtags: List<String>
)
