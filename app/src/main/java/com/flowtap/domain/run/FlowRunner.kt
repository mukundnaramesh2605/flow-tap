package com.flowtap.domain.run

import com.flowtap.domain.model.TapFlow
import kotlinx.coroutines.flow.Flow

/** Progress of one run, emitted as the runner moves through a flow's actions. */
sealed interface RunState {
    data class Running(val stepIndex: Int, val totalSteps: Int) : RunState
    data object Succeeded : RunState
    data class Failed(val stepIndex: Int, val reason: String) : RunState
}

interface FlowRunner {
    /** Cold stream: collecting it executes the flow top to bottom. */
    fun run(flow: TapFlow): Flow<RunState>
}
