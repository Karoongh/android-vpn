package com.example.sshvpn

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Thread-safe diagnostic log.
 * Keeps the latest 5,000 lines in memory and writes a rotating file in app-private storage.
 */
object LogStore {
    private const val MAX_LINES = 5_000
    private const val MAX_FILE_BYTES = 2L * 1024L * 1024L
    private val lines = ArrayDeque<String>(MAX_LINES)
    private val main = Handler(Looper.getMainLooper())
    private val writer = Executors.newSingleThreadExecutor { r ->
        Thread(r, "vpn-log-writer").apply { isDaemon = true }
    }
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile private var logFile: File? = null
    @Volatile var listener: (() -> Unit)? = null

    @Synchronized
    fun init(context: Context) {
        if (logFile != null) return
        val dir = File(context.applicationContext.filesDir, "diagnostics")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "vpn-debug.log")
        logFile = file
        if (file.exists()) {
            try {
                file.readLines().takeLast(MAX_LINES).forEach { lines.addLast(it) }
            } catch (_: Exception) {
                // Keep startup available even if an old log file is damaged.
            }
        }
    }

    fun append(msg: String) {
        val line = synchronized(this) {
            val stamp = synchronized(fmt) { fmt.format(Date()) }
            val value = "[$stamp] $msg"
            while (lines.size >= MAX_LINES) lines.removeFirst()
            lines.addLast(value)
            value
        }
        writer.execute {
            try {
                val file = logFile ?: return@execute
                if (file.exists() && file.length() >= MAX_FILE_BYTES) {
                    val old = File(file.parentFile, "vpn-debug.log.1")
                    if (old.exists()) old.delete()
                    file.renameTo(old)
                }
                file.appendText(line + "\n", Charsets.UTF_8)
            } catch (_: Exception) {
                // Logging must never crash the VPN service.
            }
        }
        main.post { listener?.invoke() }
    }

    @Synchronized
    fun clear() {
        lines.clear()
        writer.execute {
            try {
                logFile?.delete()
                logFile?.let { File(it.parentFile, "vpn-debug.log.1").delete() }
            } catch (_: Exception) {}
        }
        main.post { listener?.invoke() }
    }

    @Synchronized fun text(): String = lines.joinToString("\n")
    @Synchronized fun snapshot(): List<String> = lines.toList()
}
