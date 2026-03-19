package com.challengo.app.data.local.dao

import androidx.room.*
import com.challengo.app.data.model.Quote
import kotlinx.coroutines.flow.Flow

@Dao
interface QuoteDao {
    @Query("SELECT * FROM quotes WHERE id = 'daily_quote'")
    fun getDailyQuote(): Flow<Quote?>

    @Query("SELECT * FROM quotes WHERE id = 'daily_quote'")
    suspend fun getDailyQuoteSync(): Quote?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuote(quote: Quote)
}