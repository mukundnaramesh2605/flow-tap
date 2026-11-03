package com.flowtap.domain.model

import kotlinx.serialization.Serializable

/**
 * A named, ordered list of actions. Called "Flow" in the UI; named TapFlow in code
 * to avoid clashing with kotlinx.coroutines.flow.Flow.
 */
@Serializable
data class TapFlow(
    val id: Long = 0,
    val name: String,
    val icon: FlowIcon = FlowIcon.Bolt,
    val color: FlowColor = FlowColor.Violet,
    val actions: List<FlowAction> = emptyList(),
    val triggers: List<Trigger> = emptyList(),
)
