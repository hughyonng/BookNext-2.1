package com.booknext.app.data.repository

import com.booknext.app.data.local.db.ReadingSessionDao
import com.booknext.app.data.local.db.ReadingSessionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingSessionRepository @Inject constructor(
    private val sessionDao: ReadingSessionDao,
) {
    fun observeSessions(bookId: String): Flow<List<ReadingSessionEntity>> =
        sessionDao.observeSessions(bookId)

    suspend fun insert(session: ReadingSessionEntity) = sessionDao.insert(session)

    suspend fun getRecentSessions(bookId: String): List<ReadingSessionEntity> =
        sessionDao.getRecentSessions(bookId)

    suspend fun getAvgWpm(bookId: String): Float? = sessionDao.getAvgWpm(bookId)
}
