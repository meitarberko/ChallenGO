package com.challengo.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.challengo.app.data.local.dao.*
import com.challengo.app.data.model.*

@Database(
    entities = [
        User::class,
        Challenge::class,
        DailyChallenge::class,
        Post::class,
        Comment::class,
        PostLike::class,
        Quote::class
    ],
    version = 5,
    exportSchema = false
)
abstract class ChallenGoDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun postDao(): PostDao
    abstract fun commentDao(): CommentDao
    abstract fun postLikeDao(): PostLikeDao
    abstract fun quoteDao(): QuoteDao
}