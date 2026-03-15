package com.challengo.app.data.remote

import retrofit2.http.GET

interface ZenQuotesApi {
    @GET("random")
    suspend fun getRandomQuote(): List<QuoteResponse>
}

data class QuoteResponse(
    val q: String,
    val a: String
)