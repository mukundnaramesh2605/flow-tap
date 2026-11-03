package com.flowtap.system.shizuku

import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Shizuku user service. Lives in its own process with shell (uid 2000) privileges, so `svc`,
 * `cmd` and `appops` work without root. Keep this free of app dependencies: no Hilt, no Context.
 */
class ShellService : IShellService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                "124\ntimed out"
            } else {
                "${process.exitValue()}\n${output.trim()}"
            }
        } catch (e: Exception) {
            "127\n${e.message}"
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 15L
    }
}
