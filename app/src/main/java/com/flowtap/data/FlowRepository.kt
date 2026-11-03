package com.flowtap.data

import com.flowtap.domain.model.TapFlow
import kotlinx.coroutines.flow.Flow

interface FlowRepository {
    fun observeFlows(): Flow<List<TapFlow>>
    fun observeFlow(id: Long): Flow<TapFlow?>

    /** Inserts when [TapFlow.id] is 0, otherwise replaces. Returns the flow's id. */
    suspend fun save(flow: TapFlow): Long
    suspend fun delete(id: Long)
}
