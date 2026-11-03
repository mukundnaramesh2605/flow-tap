package com.flowtap.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.flowtap.domain.model.FlowAction
import com.flowtap.domain.model.FlowColor
import com.flowtap.domain.model.FlowIcon
import com.flowtap.domain.model.TapFlow
import com.flowtap.domain.model.Trigger

/**
 * Actions and triggers live in JSON columns: they're always read and written with their flow,
 * and the same encoding is used for export files.
 */
@Entity(tableName = "flows")
data class FlowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val color: String,
    /** Home grid order, ascending. */
    val position: Int,
    val actions: List<FlowAction>,
    val triggers: List<Trigger>,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

fun FlowEntity.toDomain(): TapFlow = TapFlow(
    id = id,
    name = name,
    icon = FlowIcon.entries.find { it.name == icon } ?: FlowIcon.Bolt,
    color = FlowColor.entries.find { it.name == color } ?: FlowColor.Violet,
    actions = actions,
    triggers = triggers,
)

fun TapFlow.toEntity(position: Int, updatedAt: Long = System.currentTimeMillis()): FlowEntity = FlowEntity(
    id = id,
    name = name,
    icon = icon.name,
    color = color.name,
    position = position,
    actions = actions,
    triggers = triggers,
    updatedAt = updatedAt,
)
