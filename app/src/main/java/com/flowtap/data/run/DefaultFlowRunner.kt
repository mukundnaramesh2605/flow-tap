package com.flowtap.data.run

import android.util.Log
import com.flowtap.domain.model.TapFlow
import com.flowtap.domain.run.ActionExecutor
import com.flowtap.domain.run.FlowRunner
import com.flowtap.domain.run.RunContext
import com.flowtap.domain.run.RunState
import com.flowtap.domain.run.StepOutcome
import com.flowtap.domain.run.StepResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Runs every action in order. A step that fails doesn't stop the rest: for a flow like Bedtime,
 * a Wi-Fi hiccup shouldn't leave the ringer loud.
 */
class DefaultFlowRunner @Inject constructor(
    private val executor: ActionExecutor,
) : FlowRunner {

    override fun run(flow: TapFlow, context: RunContext): Flow<RunState> = flow {
        val outcomes = mutableListOf<StepOutcome>()
        flow.actions.forEachIndexed { index, action ->
            emit(RunState.Running(stepIndex = index, totalSteps = flow.actions.size))
            val result = try {
                executor.execute(action, context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Executors handle expected failures; this is the last line of defence against a crash.
                Log.e(TAG, "Action $action crashed", e)
                StepResult.Failed("Unexpected error: ${e.message ?: e::class.simpleName}")
            }
            outcomes += StepOutcome(index, action, result)
        }
        emit(RunState.Finished(flow.name, outcomes))
    }

    private companion object {
        const val TAG = "FlowRunner"
    }
}
