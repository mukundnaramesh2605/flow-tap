package com.flowtap.data.db

import androidx.room.TypeConverter
import com.flowtap.data.FlowTapJson
import com.flowtap.domain.model.FlowAction
import com.flowtap.domain.model.Trigger
import kotlinx.serialization.builtins.ListSerializer

class Converters {
    @TypeConverter
    fun actionsToJson(value: List<FlowAction>): String =
        FlowTapJson.encodeToString(ListSerializer(FlowAction.serializer()), value)

    @TypeConverter
    fun actionsFromJson(value: String): List<FlowAction> =
        FlowTapJson.decodeFromString(ListSerializer(FlowAction.serializer()), value)

    @TypeConverter
    fun triggersToJson(value: List<Trigger>): String =
        FlowTapJson.encodeToString(ListSerializer(Trigger.serializer()), value)

    @TypeConverter
    fun triggersFromJson(value: String): List<Trigger> =
        FlowTapJson.decodeFromString(ListSerializer(Trigger.serializer()), value)
}
