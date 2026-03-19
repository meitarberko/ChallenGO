package com.challengo.app.data.repository

import com.challengo.app.data.local.dao.QuoteDao
import com.challengo.app.data.model.Quote
import com.challengo.app.data.remote.RetrofitClient
import com.challengo.app.data.remote.QuoteResponse
import kotlinx.coroutines.flow.Flow

class QuoteRepository(
    private val quoteDao: QuoteDao
) {
    suspend fun fetchDailyQuote(): Result<Quote> {
        return try {
            val response = RetrofitClient.zenQuotesApi.getRandomQuote()
            if (response.isNotEmpty()) {
                val quoteResponse = response[0]
                val quote = Quote(
                    id = "daily_quote",
                    text = quoteResponse.q,
                    author = quoteResponse.a,
                    timestamp = System.currentTimeMillis()
                )

                // Cache locally
                quoteDao.insertQuote(quote)

                Result.success(quote)
            } else {
                Result.failure(Exception("Empty response"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getDailyQuote(): Flow<Quote?> {
        return quoteDao.getDailyQuote()
    }

    suspend fun getDailyQuoteSync(): Quote? {
        return quoteDao.getDailyQuoteSync()
    }
}