package com.flowtap.data.run

import com.flowtap.di.ApplicationScope
import com.flowtap.domain.model.TapFlow
import com.flowtap.domain.run.FlowRunner
import com.flowtap.domain.run.RunContext
import com.flowtap.domain.run.RunState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns every in-flight run, in a process-wide scope, so leaving the screen (or a long Wait step)
 * doesn't cancel a flow halfway. One run per flow at a time.
 */
@Singleton
class FlowRunManager @Inject constructor(
    private val runner: FlowRunner,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val _states = MutableStateFlow<Map<Long, RunState>>(emptyMap())

    /** Live run progress keyed by flow id; absent means idle. */
    val states: StateFlow<Map<Long, RunState>> = _states.asStateFlow()

    private val runs = mutableMapOf<Long, Deferred<RunState.Finished>>()
    private val clearJobs = mutableMapOf<Long, Job>()

    /** Starts [flow], or returns the run already in progress for it. Completes with the final report. */
    @Synchronized
    fun start(flow: TapFlow, interactive: Boolean): Deferred<RunState.Finished> {
        runs[flow.id]?.takeIf { it.isActive }?.let { return it }
        clearJobs.remove(flow.id)?.cancel()

        val run = scope.async {
            var finished = RunState.Finished(flow.name, emptyList())
            runner.run(flow, RunContext(interactive, flow.name)).collect { state ->
                _states.update { it + (flow.id to state) }
                if (state is RunState.Finished) finished = state
            }
            finished
        }
        runs[flow.id] = run
        run.invokeOnCompletion { scheduleClear(flow.id) }
        return run
    }

    fun isRunning(flowId: Long): Boolean = runs[flowId]?.isActive == true

    // Leave the success/failure badge up briefly before returning the tile to idle.
    @Synchronized
    private fun scheduleClear(flowId: Long) {
        clearJobs[flowId] = scope.launch {
            delay(RESULT_LINGER_MS)
            _states.update { it - flowId }
        }
    }

    private companion object {
        const val RESULT_LINGER_MS = 2_500L
    }
}
