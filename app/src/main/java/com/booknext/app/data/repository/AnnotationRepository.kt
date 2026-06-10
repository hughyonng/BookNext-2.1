package com.booknext.app.data.repository

import com.booknext.app.data.local.db.AnnotationDao
import com.booknext.app.data.local.db.AnnotationEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnnotationRepository @Inject constructor(
    private val annotationDao: AnnotationDao,
) {
    fun observeByBook(bookId: String): Flow<List<AnnotationEntity>> =
        annotationDao.observeByBook(bookId)

    suspend fun upsert(annotation: AnnotationEntity) = annotationDao.upsert(annotation)

    suspend fun deleteById(id: String) = annotationDao.deleteById(id)
}
