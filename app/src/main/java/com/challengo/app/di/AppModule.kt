package com.challengo.app.di

import android.app.Application
import androidx.room.Room
import com.challengo.app.data.local.ChallenGoDatabase
import com.challengo.app.data.repository.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object AppModule {
    private var database: ChallenGoDatabase? = null
    
    fun provideDatabase(application: Application): ChallenGoDatabase {
        if (database == null) {
            database = Room.databaseBuilder(
                application,
                ChallenGoDatabase::class.java,
                "challengo_database"
            ).fallbackToDestructiveMigration().build()
        }
        return requireNotNull(database)
    }
    
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()
    
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
    
    fun provideAuthRepository(
        firebaseAuth: FirebaseAuth,
        firestore: FirebaseFirestore,
        userDao: com.challengo.app.data.local.dao.UserDao
    ): AuthRepository = AuthRepository(firebaseAuth, firestore, userDao)
    
    fun provideChallengeRepository(
        firestore: FirebaseFirestore,
        challengeDao: com.challengo.app.data.local.dao.ChallengeDao
    ): ChallengeRepository = ChallengeRepository(firestore, challengeDao)
    
    fun providePostRepository(
        firestore: FirebaseFirestore,
        postDao: com.challengo.app.data.local.dao.PostDao
    ): PostRepository = PostRepository(firestore, postDao)
    
    fun provideCommentRepository(
        firestore: FirebaseFirestore,
        commentDao: com.challengo.app.data.local.dao.CommentDao,
        notificationRepository: NotificationRepository
    ): CommentRepository = CommentRepository(firestore, commentDao, notificationRepository)
    
    fun provideLikeRepository(
        firestore: FirebaseFirestore,
        postLikeDao: com.challengo.app.data.local.dao.PostLikeDao,
        notificationRepository: NotificationRepository,
        postRepository: PostRepository
    ): LikeRepository = LikeRepository(firestore, postLikeDao, notificationRepository, postRepository)
    
    fun provideQuoteRepository(
        quoteDao: com.challengo.app.data.local.dao.QuoteDao
    ): QuoteRepository = QuoteRepository(quoteDao)
    
    fun provideUserRepository(
        firestore: FirebaseFirestore,
        userDao: com.challengo.app.data.local.dao.UserDao
    ): UserRepository = UserRepository(firestore, userDao)

    fun provideNotificationRepository(
        firestore: FirebaseFirestore
    ): NotificationRepository = NotificationRepository(firestore)
}
