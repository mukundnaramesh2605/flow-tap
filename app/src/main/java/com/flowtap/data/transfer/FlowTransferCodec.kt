package com.flowtap.data.transfer

import com.flowtap.data.FlowTapJson
import com.flowtap.domain.model.FlowAction
import com.flowtap.domain.model.FlowColor
import com.flowtap.domain.model.FlowIcon
import com.flowtap.domain.model.TapFlow
import com.flowtap.domain.model.Trigger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.UUID

/** Thrown when a file can't be read as a FlowTap export at all. */
class ImportFormatException(message: String) : Exception(message)

/** A flow read from an export file, plus what had to be dropped to read it. */
data class DecodedFlow(
    val flow: TapFlow,
    /** Actions whose type or fields this version doesn't understand. */
    val skippedActions: Int,
    val skippedTriggers: Int,
)

data class DecodedFile(
    val flows: List<DecodedFlow>,
    /** Flow entries that couldn't be read at all (e.g. missing a name). */
    val skippedFlows: Int,
)

/**
 * Export file format. Writing is strict; reading goes element by element so one unknown action
 * (say, from a newer FlowTap) drops just that action instead of failing the whole import.
 */
object FlowTransferCodec {
    const val FORMAT = "flowtap"
    const val VERSION = 1

    @Serializable
    private data class ExportFile(
        val format: String = FORMAT,
        val version: Int = VERSION,
        val exportedAt: String,
        val flows: List<ExportedFlow>,
    )

    @Serializable
    private data class ExportedFlow(
        val name: String,
        val icon: FlowIcon,
        val color: FlowColor,
        val actions: List<FlowAction>,
        val triggers: List<Trigger>,
    )

    private val prettyJson = Json(FlowTapJson) { prettyPrint = true }

    fun encode(flows: List<TapFlow>, exportedAt: String): String = prettyJson.encodeToString(
        ExportFile.serializer(),
        ExportFile(
            exportedAt = exportedAt,
            flows = flows.map { ExportedFlow(it.name, it.icon, it.color, it.actions, it.triggers) },
        ),
    )

    /**
     * Imported flows get id 0 and fresh trigger ids, so importing the same file twice (or onto a phone
     * that already has the sample flows) never produces clashing schedules.
     */
    fun decode(text: String): DecodedFile {
        val root = runCatching { FlowTapJson.parseToJsonElement(text) }.getOrNull() as? JsonObject
            ?: throw ImportFormatException("This file isn't valid JSON.")
        if (root.string("format") != FORMAT) {
            throw ImportFormatException("This doesn't look like a FlowTap export.")
        }
        // Files from newer versions are still read; anything this version doesn't know is skipped below.
        val entries = root["flows"] as? JsonArray ?: throw ImportFormatException("The file has no flows in it.")

        var skippedFlows = 0
        val flows = entries.mapNotNull { element ->
            decodeFlow(element).also { if (it == null) skippedFlows++ }
        }
        return DecodedFile(flows, skippedFlows)
    }

    private fun decodeFlow(element: JsonElement): DecodedFlow? {
        val obj = element as? JsonObject ?: return null
        val name = obj.string("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        val (actions, skippedActions) = decodeEach(obj["actions"]) {
            FlowTapJson.decodeFromJsonElement(FlowAction.serializer(), it)
        }
        val (triggers, skippedTriggers) = decodeEach(obj["triggers"]) {
            FlowTapJson.decodeFromJsonElement(Trigger.serializer(), it)
        }
        return DecodedFlow(
            flow = TapFlow(
                id = 0,
                name = name,
                icon = FlowIcon.entries.find { it.name == obj.string("icon") } ?: FlowIcon.Bolt,
                color = FlowColor.entries.find { it.name == obj.string("color") } ?: FlowColor.Violet,
                actions = actions,
                triggers = triggers.map { it.withId(UUID.randomUUID().toString()) },
            ),
            skippedActions = skippedActions,
            skippedTriggers = skippedTriggers,
        )
    }

    /** Decodes each array element on its own; returns the ones that worked and how many didn't. */
    private inline fun <T : Any> decodeEach(element: JsonElement?, decode: (JsonElement) -> T): Pair<List<T>, Int> {
        val array = element as? JsonArray ?: return emptyList<T>() to 0
        var skipped = 0
        val items = array.mapNotNull { item ->
            runCatching { decode(item) }.getOrNull().also { if (it == null) skipped++ }
        }
        return items to skipped
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun Trigger.withId(id: String): Trigger = when (this) {
        is Trigger.TimeOfDay -> copy(id = id)
    }
}
