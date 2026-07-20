package com.v2ir.data.xray

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XrayProcessRunner @Inject constructor() {

    @Volatile private var process: Process? = null
    @Volatile private var logThread: Thread? = null

    var onStdoutLine: ((String) -> Unit)? = null
    var onStderrLine: ((String) -> Unit)? = null
    var onProcessExit: ((Int) -> Unit)? = null

    suspend fun start(
        binary: File,
        configFile: File,
        workingDir: File
    ): Boolean = withContext(Dispatchers.IO) {
        stopInternal()
        if (!binary.canExecute()) binary.setExecutable(true, false)
        return@withContext try {
            val pb = ProcessBuilder(
                binary.absolutePath,
                "run",
                "-c",
                configFile.absolutePath
            )
            onStdoutLine?.invoke("Executing: ${binary.absolutePath} run -c ${configFile.absolutePath}")
            pb.directory(workingDir)
            pb.redirectErrorStream(true) // Merge stderr into stdout for unified logging
            pb.environment()["XRAY_LOCATION_ASSET"] = workingDir.absolutePath
            val proc = pb.start()
            process = proc
            startLogReader(proc)
            startExitWatcher(proc)
            true
        } catch (e: Exception) {
            onStderrLine?.invoke("Process start failed: ${e.message}")
            stopInternal()
            false
        }
    }

    fun stop() {
        stopInternal()
    }

    fun isRunning(): Boolean = process?.isAlive == true

    private fun stopInternal() {
        // Interrupt log reader thread before destroying the process to avoid
        // the reader blocking on a closed stream
        logThread?.interrupt()
        logThread = null

        process?.let { proc ->
            try {
                proc.destroy()
                // Give it a short window to exit gracefully before force-killing
                val exited = proc.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
                if (!exited) proc.destroyForcibly()
            } catch (_: Exception) {
                runCatching { proc.destroyForcibly() }
            }
        }
        process = null
    }

    private fun startLogReader(proc: Process) {
        // redirectErrorStream(true) merges all output into inputStream — one thread is enough
        logThread = Thread {
            try {
                BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                    var line = reader.readLine()
                    while (line != null && !Thread.currentThread().isInterrupted) {
                        onStdoutLine?.invoke(line)
                        line = reader.readLine()
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt() // Restore interrupted status
            } catch (_: Exception) {
                // Stream closed when process dies — expected, not an error
            }
        }.apply {
            isDaemon = true
            name = "xray-log-reader"
            start()
        }
    }

    private fun startExitWatcher(proc: Process) {
        Thread {
            try {
                val code = proc.waitFor()
                onProcessExit?.invoke(code)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                onProcessExit?.invoke(-1)
            } catch (_: Exception) {
                onProcessExit?.invoke(-1)
            }
        }.apply {
            isDaemon = true
            name = "xray-exit-watcher"
            start()
        }
    }
}




