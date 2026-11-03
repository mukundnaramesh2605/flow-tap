package com.flowtap.domain.run

import com.flowtap.domain.model.FlowAction
import com.flowtap.domain.model.TapFlow
import kotlinx.coroutines.flow.Flow

/** How a run was started. Headless runs can't show UI, so actions pick a background-safe path. */
data class RunContext(
    /** True when the user tapped the flow in the app; false for automations. */
    val interactive: Boolean,
    val flowName: String,
)

/** Something the user can do to let a blocked step run next time. */
sealed interface Fix {
    data class GrantPermissions(val permissions: List<String>) : Fix

    /** Opens a system settings screen; [forThisApp] adds FlowTap's package to the intent. */
    data class OpenSettings(val action: String, val forThisApp: Boolean = false) : Fix
}

sealed interface StepResult {
    data object Done : StepResult

    /** Couldn't finish silently, so the user was handed the last step (panel opened, notification posted). */
    data class HandedOff(val message: String) : StepResult

    /** Didn't run because something is missing; [fix] resolves it when present. */
    data class Blocked(val message: String, val fix: Fix? = null) : StepResult

    /** Tried and failed, or impossible on this device. */
    data class Failed(val message: String) : StepResult
}

data class StepOutcome(val index: Int, val action: FlowAction, val result: StepResult)

/** Progress of one run, emitted as the runner moves through a flow's actions. */
sealed interface RunState {
    data class Running(val stepIndex: Int, val totalSteps: Int) : RunState

    data class Finished(val flowName: String, val outcomes: List<StepOutcome>) : RunState {
        /** Steps that need the user's attention: everything except clean successes. */
        val issues: List<StepOutcome> get() = outcomes.filter { it.result !is StepResult.Done }
        val allFailed: Boolean get() = outcomes.isNotEmpty() && outcomes.all { it.result is StepResult.Failed || it.result is StepResult.Blocked }
    }
}

interface FlowRunner {
    /** Cold stream: collecting it executes the flow top to bottom, ending with [RunState.Finished]. */
    fun run(flow: TapFlow, context: RunContext): Flow<RunState>
}

interface ActionExecutor {
    suspend fun execute(action: FlowAction, context: RunContext): StepResult
}
