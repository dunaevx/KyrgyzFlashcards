package com.example.kyrgyzflashcards.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyDao {
    @Query("SELECT * FROM word_sets ORDER BY createdAt DESC")
    fun observeSets(): Flow<List<WordSet>>

    @Query("SELECT * FROM words WHERE setId = :setId ORDER BY id")
    fun observeWords(setId: Long): Flow<List<Word>>

    @Query("SELECT * FROM words WHERE setId IN (:setIds) ORDER BY RANDOM()")
    suspend fun wordsForSets(setIds: List<Long>): List<Word>

    @Insert
    suspend fun insertSet(set: WordSet): Long

    @Insert
    suspend fun insertWords(words: List<Word>)

    @Update
    suspend fun updateWord(word: Word)

    @Query("DELETE FROM words WHERE setId = :setId")
    suspend fun deleteWordsForSet(setId: Long)

    @Query("DELETE FROM word_sets WHERE id = :setId")
    suspend fun deleteSet(setId: Long)

    @Query("UPDATE word_sets SET name = :name WHERE id = :setId")
    suspend fun renameSet(setId: Long, name: String)

    @Query("SELECT COUNT(*) FROM words WHERE setId = :setId")
    suspend fun wordCount(setId: Long): Int

    @Query("SELECT COUNT(*) FROM words WHERE setId = :setId AND learned = 1")
    suspend fun learnedCount(setId: Long): Int
}

@Database(entities = [WordSet::class, Word::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): StudyDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "kyrgyz_flashcards.db"
            ).build().also { INSTANCE = it }
        }
    }
}
