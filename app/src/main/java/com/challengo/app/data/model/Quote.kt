package com.challengo.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quotes")
data class Quote(
    @PrimaryKey
    val id: String = "daily_quote",
    val text: String,
    val author: String,
    val timestamp: Long = System.currentTimeMillis()
)
