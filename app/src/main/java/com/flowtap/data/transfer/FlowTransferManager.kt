package com.flowtap.data.transfer

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.flowtap.data.FlowRepository
import com.flowtap.data.apps.AppCatalog
import com.flowtap.domain.model.FlowAction
import com.flowtap.domain.model.TapFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** A flow ready to import, with anything the user should know before confirming. */
data class ImportCandidate(
    val flow: TapFlow,
    /** Labels of apps the flow opens that aren't installed here. Kept, but flagged. */
    val missingApps: List<String>,
    val skippedActions: Int,
    val skippedTriggers: Int,
)

data class ImportPreview(
    val candidates: List<ImportCandidate>,
    val skippedFlows: Int,
)

/** A prepared export: the JSON plus a suggested file name. */
data class ExportPayload(val fileName: String, val json: String, val flowCount: Int)

@Singleton
class FlowTransferManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: FlowRepository,
    private val appCatalog: AppCatalog,
) {

    /** Exports the given flows, or every flow when [flowIds] is null. */
    suspend fun prepareExport(flowIds: Set<Long>? = null): ExportPayload {
        val flows = repository.getFlows().filter { flowIds == null || it.id in flowIds }
        val json = FlowTransferCodec.encode(flows, OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        val fileName = if (flows.size == 1) {
            "flowtap-${flows.single().name.toFileSlug()}.json"
        } else {
            "flowtap-flows-${LocalDate.now()}.json"
        }
        return ExportPayload(fileName, json, flows.size)
    }

    suspend fun writeTo(uri: Uri, payload: ExportPayload) = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Couldn't open file")
        stream.bufferedWriter().use { it.write(payload.json) }
    }

    /** Writes the payload to app cache and returns a content:// Uri other apps can read. */
    suspend fun shareableUri(payload: ExportPayload): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, payload.fileName).apply { writeText(payload.json) }
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    suspend fun readPreview(uri: Uri): ImportPreview = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readNBytesCompat(MAX_IMPORT_BYTES + 1)
            if (bytes.size > MAX_IMPORT_BYTES) throw ImportFormatException("That file is too large to be a FlowTap export.")
            bytes.decodeToString()
        } ?: throw IOException("Couldn't open file")

        val decoded = FlowTransferCodec.decode(text)
        ImportPreview(
            candidates = decoded.flows.map { d ->
                ImportCandidate(
                    flow = d.flow,
                    missingApps = d.flow.actions
                        .filterIsInstance<FlowAction.OpenApp>()
                        .filterNot { appCatalog.isInstalled(it.packageName) }
                        .map { it.appLabel },
                    skippedActions = d.skippedActions,
                    skippedTriggers = d.skippedTriggers,
                )
            },
            skippedFlows = decoded.skippedFlows,
        )
    }

    suspend fun import(preview: ImportPreview): Int =
        repository.addAll(preview.candidates.map { it.flow }).size

    private fun String.toFileSlug(): String =
        lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "flow" }

    // InputStream.readNBytes is API 33+.
    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private companion object {
        const val SHARE_DIR = "exports"
        const val MAX_IMPORT_BYTES = 2 * 1024 * 1024
    }
}
