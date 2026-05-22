package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_history ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(entry: ChatEntry): Long

    @Query("DELETE FROM chat_history WHERE id = :id")
    suspend fun deleteMessageById(id: Long)

    @Query("DELETE FROM chat_history")
    suspend fun clearHistory()
}
