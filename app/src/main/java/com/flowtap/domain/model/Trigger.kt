package com.flowtap.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek

/** What makes a flow run on its own. A flow may have several. Manual taps always work. */
@Serializable
sealed interface Trigger {
    val id: String
    val enabled: Boolean

    /**
     * Fires at [hour]:[minute] local time on each of [days] (ISO day numbers, 1 = Monday .. 7 = Sunday).
     */
    @Serializable
    @SerialName("time")
    data class TimeOfDay(
        override val id: String,
        val hour: Int,
        val minute: Int,
        val days: Set<Int> = ALL_DAYS,
        override val enabled: Boolean = true,
    ) : Trigger {
        val daysOfWeek: List<DayOfWeek> get() = days.sorted().map(DayOfWeek::of)

        companion object {
            val ALL_DAYS: Set<Int> = (1..7).toSet()
            val WEEKDAYS: Set<Int> = (1..5).toSet()
            val WEEKEND: Set<Int> = setOf(6, 7)
        }
    }
}
