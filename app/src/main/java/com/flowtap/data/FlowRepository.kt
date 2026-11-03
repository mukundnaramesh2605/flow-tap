package com.flowtap.data

import com.flowtap.domain.model.TapFlow
import kotlinx.coroutines.flow.Flow

interface FlowRepository {
    fun observeFlows(): Flow<List<TapFlow>>
    fun observeFlow(id: Long): Flow<TapFlow?>

    suspend fun getFlows(): List<TapFlow>
    suspend fun getFlow(id: Long): TapFlow?

    /** Inserts when [TapFlow.id] is 0 (appended to the end of the grid), otherwise replaces. Returns the id. */
    suspend fun save(flow: TapFlow): Long

    /** Inserts [flows] as new flows at the end of the grid, ignoring their ids. */
    suspend fun addAll(flows: List<TapFlow>): List<Long>

    suspend fun delete(id: Long)
}
