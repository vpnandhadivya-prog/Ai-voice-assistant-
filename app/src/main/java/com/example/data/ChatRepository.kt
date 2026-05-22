package com.example.data

import kotlinx.coroutines.flow.Flow

class ChatRepository(private val chatDao: ChatDao) {
    val allMessages: Flow<List<ChatEntry>> = chatDao.getAllMessages()

    suspend fun insertMessage(text: String, isUser: Boolean): Long {
        val entry = ChatEntry(text = text, isUser = isUser)
        return chatDao.insertMessage(entry)
    }

    suspend fun deleteMessage(id: Long) {
        chatDao.deleteMessageById(id)
    }

    suspend fun clearHistory() {
        chatDao.clearHistory()
    }
}
