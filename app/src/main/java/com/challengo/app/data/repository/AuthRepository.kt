package com.challengo.app.data.repository

import android.net.Uri
import android.util.Log
import com.challengo.app.data.local.dao.UserDao
import com.challengo.app.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userDao: UserDao
) {
    companion object {
        private const val TAG = "AuthRepository"
        const val ERROR_EMAIL_ALREADY_REGISTERED = "error_email_already_registered"
        const val ERROR_USERNAME_TAKEN = "error_username_taken"
        const val ERROR_INVALID_CREDENTIALS = "error_invalid_credentials"
    }

    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    suspend fun register(
        email: String,
        password: String,
        username: String,
        firstName: String,
        lastName: String,
        age: Int,
        selectedImageUri: Uri?
    ): Result<FirebaseUser> {
        var createdAuthUser: FirebaseUser? = null
        return try {
            Log.d(
                TAG,
                "event=register_start authUid=${currentAuthUid() ?: "null"} email=$email"
            )
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: return Result.failure(Exception("User creation failed"))
            createdAuthUser = user
            Log.d(
                TAG,
                "event=register_auth_created returnedUid=${user.uid} currentAuthUid=${currentAuthUid() ?: "null"}"
            )
            user.getIdToken(true).await()
            Log.d(
                TAG,
                "event=register_token_ready uid=${user.uid} currentAuthUid=${currentAuthUid() ?: "null"}"
            )
            val profileImageUri = selectedImageUri?.toString()
            val usernameLower = username.trim().lowercase()
            reserveUsernameAndCreateProfile(
                user = user,
                email = email,
                firstName = firstName,
                lastName = lastName,
                username = username.trim(),
                usernameLower = usernameLower,
                age = age,
                profileImageUri = profileImageUri
            )

            val localUser = User(
                uid = user.uid,
                username = username.trim(),
                email = email,
                firstName = firstName,
                lastName = lastName,
                age = age,
                profileImageUri = profileImageUri,
                totalPoints = 0,
                challengesCompleted = 0,
                level = 1
            )
            userDao.insertUser(localUser)

            Result.success(user)
        } catch (e: FirebaseAuthUserCollisionException) {
            firebaseAuth.signOut()
            Result.failure(Exception(ERROR_EMAIL_ALREADY_REGISTERED))
        } catch (e: Exception) {
            Log.e(
                TAG,
                "event=register_fail authUid=${currentAuthUid() ?: "null"} message=${e.message}",
                e
            )
            cleanupOrphanAuthUser(createdAuthUser)
            Result.failure(mapRegisterError(e))
        }
    }

    suspend fun login(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: return Result.failure(Exception("Login failed"))
            loadUserData(user.uid)
            Result.success(user)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(Exception(ERROR_INVALID_CREDENTIALS))
        } catch (e: FirebaseAuthInvalidUserException) {
            Result.failure(Exception(ERROR_INVALID_CREDENTIALS))
        } catch (e: Exception) {
            Log.e(TAG, "event=login_fail", e)
            Result.failure(e)
        }
    }

    suspend fun loadUserData(uid: String) {
        try {
            Log.d(
                TAG,
                "event=load_user_data_start authUid=${currentAuthUid() ?: "null"} path=users/$uid"
            )
            val userDoc = firestore.collection("users").document(uid).get().await()
            if (userDoc.exists()) {
                val user = User(
                    uid = userDoc.getString("uid") ?: uid,
                    username = userDoc.getString("username") ?: "",
                    email = userDoc.getString("email") ?: "",
                    firstName = userDoc.getString("firstName") ?: "",
                    lastName = userDoc.getString("lastName") ?: "",
                    age = userDoc.getLong("age")?.toInt() ?: 0,
                    profileImageUri = userDoc.getString("profileImageUri") ?: userDoc.getString("profileImageUrl"),
                    totalPoints = (userDoc.getLong("totalPoints")
                        ?: userDoc.getLong("points")
                        ?: 0L).toInt(),
                    challengesCompleted = userDoc.getLong("challengesCompleted")?.toInt() ?: 0,
                    level = userDoc.getLong("level")?.toInt()
                        ?: (((userDoc.getLong("totalPoints") ?: userDoc.getLong("points") ?: 0L).toInt() / 50) + 1),
                    lastChallengeCompletedAt = userDoc.getLong("lastChallengeCompletedAt"),
                    currentStreak = userDoc.getLong("currentStreak")?.toInt() ?: 0
                )
                userDao.insertUser(user)
            }
            Log.d(
                TAG,
                "event=load_user_data_success authUid=${currentAuthUid() ?: "null"} path=users/$uid exists=${userDoc.exists()}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "event=load_user_data_fail uid=$uid", e)
        }
    }

    fun logout() {
        firebaseAuth.signOut()
    }

    fun isLoggedIn(): Boolean {
        return firebaseAuth.currentUser != null
    }

    private suspend fun reserveUsernameAndCreateProfile(
        user: FirebaseUser,
        email: String,
        firstName: String,
        lastName: String,
        username: String,
        usernameLower: String,
        age: Int,
        profileImageUri: String?
    ) {
        val usernameRef = firestore.collection("usernames").document(usernameLower)
        val userRef = firestore.collection("users").document(user.uid)
        Log.d(
            TAG,
            "event=reserve_profile_start authUid=${currentAuthUid() ?: "null"} usernamePath=${usernameRef.path} userPath=${userRef.path} targetUid=${user.uid}"
        )

        firestore.runTransaction { transaction ->
            val usernameSnap = transaction.get(usernameRef)
            if (usernameSnap.exists()) {
                throw UsernameTakenException()
            }
            val userSnap = transaction.get(userRef)
            val existingPoints = (userSnap.getLong("points")
                ?: userSnap.getLong("totalPoints")
                ?: 0L).toInt()
            @Suppress("UNCHECKED_CAST")
            val existingHashtags = (userSnap.get("hashtags") as? List<String>)
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val pointsToStore = if (existingPoints > 0) existingPoints else 0
            val levelToStore = (pointsToStore / 50) + 1
            val hashtagsToStore = if (existingHashtags.isNotEmpty()) existingHashtags else emptyList()

            val createdAt = FieldValue.serverTimestamp()
            transaction.set(
                usernameRef,
                hashMapOf(
                    "uid" to user.uid,
                    "createdAt" to createdAt
                )
            )

            transaction.set(
                userRef,
                hashMapOf(
                    "uid" to user.uid,
                    "firstName" to firstName,
                    "lastName" to lastName,
                    "username" to username,
                    "usernameLower" to usernameLower,
                    "age" to age,
                    "email" to email,
                    "points" to pointsToStore,
                    "hashtags" to hashtagsToStore,
                    "createdAt" to createdAt,
                    "totalPoints" to pointsToStore,
                    "challengesCompleted" to 0,
                    "level" to levelToStore,
                    "profileImageUri" to profileImageUri,
                    "lastChallengeCompletedAt" to null,
                    "currentStreak" to 0,
                    "activeChallengeId" to null,
                    "activeChallengeHashtag" to null,
                    "activeChallengeRolledAt" to null,
                    "activeChallengeCompleted" to false,
                    "completedChallengeIds" to emptyList<String>()
                )
            )
        }.await()
        Log.d(
            TAG,
            "event=reserve_profile_success authUid=${currentAuthUid() ?: "null"} usernamePath=${usernameRef.path} userPath=${userRef.path}"
        )
    }

    private suspend fun cleanupOrphanAuthUser(createdAuthUser: FirebaseUser?) {
        try {
            createdAuthUser?.delete()?.await()
        } catch (e: Exception) {
            Log.e(TAG, "event=cleanup_orphan_fail", e)
        } finally {
            firebaseAuth.signOut()
        }
    }

    private fun mapRegisterError(error: Exception): Exception {
        return when (error) {
            is UsernameTakenException -> Exception(ERROR_USERNAME_TAKEN)
            else -> {
                val causeTaken = generateSequence(error as Throwable?) { it.cause }
                    .any { it is UsernameTakenException || it.message?.contains("USERNAME_TAKEN") == true }
                if (causeTaken) Exception(ERROR_USERNAME_TAKEN) else error
            }
        }
    }

    private fun currentAuthUid(): String? = firebaseAuth.currentUser?.uid
}

private class UsernameTakenException : RuntimeException("USERNAME_TAKEN")
