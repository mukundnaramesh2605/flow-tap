package com.flowtap.data

import com.flowtap.domain.model.TapFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** Phase 1 stand-in for the Room-backed repository. Data resets on process death. */
@Singleton
class InMemoryFlowRepository @Inject constructor() : FlowRepository {

    private val nextId = AtomicLong(1)
    private val flows: MutableStateFlow<List<TapFlow>> =
        MutableStateFlow(SampleFlows.all.map { it.copy(id = nextId.getAndIncrement()) })

    override fun observeFlows(): StateFlow<List<TapFlow>> = flows

    override fun observeFlow(id: Long): Flow<TapFlow?> = flows.map { list -> list.find { it.id == id } }

    override suspend fun save(flow: TapFlow): Long {
        val saved = if (flow.id == 0L) flow.copy(id = nextId.getAndIncrement()) else flow
        flows.update { list ->
            if (list.any { it.id == saved.id }) list.map { if (it.id == saved.id) saved else it }
            else list + saved
        }
        return saved.id
    }

    override suspend fun delete(id: Long) {
        flows.update { list -> list.filterNot { it.id == id } }
    }
}
