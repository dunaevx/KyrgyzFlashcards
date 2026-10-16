package com.example.kyrgyzflashcards.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "word_sets")
data class WordSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "words")
data class Word(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val setId: Long,
    val kyrgyz: String,
    val russian: String,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val streak: Int = 0,
    val learned: Boolean = false,
    val lastReviewed: Long = 0L
)
