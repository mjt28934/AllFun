package app.allfun.data.repo

import app.allfun.data.db.SourceDao
import app.allfun.data.db.SourceEntity
import kotlinx.coroutines.flow.Flow

class SourceRepository(private val dao: SourceDao) {
    fun observeAll(): Flow<List<SourceEntity>> = dao.observeAll()

    suspend fun all(): List<SourceEntity> = dao.all()

    suspend fun byId(id: String): SourceEntity? = dao.byId(id)

    suspend fun upsert(source: SourceEntity) = dao.upsert(source)

    suspend fun setEnabled(id: String, enabled: Boolean) = dao.setEnabled(id, enabled)

    suspend fun delete(id: String) = dao.delete(id)
}
