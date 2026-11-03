package com.flowtap.data.run

import com.flowtap.domain.model.FlowAction
import com.flowtap.domain.model.TapFlow
import com.flowtap.domain.run.FlowRunner
import com.flowtap.domain.run.RunState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Phase 1 runner: walks the steps with a short pause each so the tile feedback can be
 * seen, without touching the device. Replaced by the real executor in phase 3.
 */
class PreviewFlowRunner @Inject constructor() : FlowRunner {
    override fun run(flow: TapFlow): Flow<RunState> = flow {
        flow.actions.forEachIndexed { index, action ->
            emit(RunState.Running(stepIndex = index, totalSteps = flow.actions.size))
            delay(if (action is FlowAction.Delay) 1_000L else STEP_MS)
        }
        emit(RunState.Succeeded)
    }

    private companion object {
        const val STEP_MS = 450L
    }
}
