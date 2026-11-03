package com.flowtap.data

import com.flowtap.data.db.FlowDao
import com.flowtap.data.db.toDomain
import com.flowtap.data.db.toEntity
import com.flowtap.domain.model.TapFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomFlowRepository @Inject constructor(
    private val dao: FlowDao,
) : FlowRepository {

    override fun observeFlows(): Flow<List<TapFlow>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeFlow(id: Long): Flow<TapFlow?> =
        dao.observe(id).map { it?.toDomain() }

    override suspend fun getFlows(): List<TapFlow> = dao.getAll().map { it.toDomain() }

    override suspend fun getFlow(id: Long): TapFlow? = dao.get(id)?.toDomain()

    override suspend fun save(flow: TapFlow): Long {
        val existing = if (flow.id == 0L) null else dao.get(flow.id)
        return if (existing == null) {
            dao.appendAll(listOf(flow.toEntity(position = 0))).single()
        } else {
            dao.update(flow.toEntity(position = existing.position))
            flow.id
        }
    }

    override suspend fun addAll(flows: List<TapFlow>): List<Long> =
        dao.appendAll(flows.map { it.toEntity(position = 0) })

    override suspend fun delete(id: Long) = dao.delete(id)
}
