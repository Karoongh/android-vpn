package com.example.sshvpn

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

enum class LogSeverity { INFO, WARNING, ERROR }

data class LogEntry(
    val timestamp: Long,
    val severity: LogSeverity,
    val message: String,
    val isContext: Boolean = false,
)

/**
 * Bounded diagnostic log:
 * - ordinary entries live in memory for one hour;
 * - a five-minute in-memory breadcrumb window is attached to critical errors;
 * - critical errors and their breadcrumbs are persisted independently for six hours.
 *
 * No always-on cleanup service is used. Expiry is checked on append, read, startup,
 * and once per minute while the app process is alive.
 */
object LogStore {
    private const val NORMAL_RETENTION_MS = 60L * 60L * 1000L
    private const val ERROR_RETENTION_MS = 6L * 60L * 60L * 1000L
    private const val CONTEXT_RETENTION_MS = 5L * 60L * 1000L
    private const val MAX_NORMAL_ENTRIES = 2_000
    private const val MAX_CONTEXT_ENTRIES = 300
    private const val MAX_CONTEXT_PER_ERROR = 40
    private const val MAX_MESSAGE_CHARS = 2_000
    private const val MAX_ERROR_RECORDS = 100
    private const val MAX_ERROR_FILE_BYTES = 256L * 1024L
    private const val PRUNE_INTERVAL_MS = 60_000L

    private data class ErrorRecord(
        val entry: LogEntry,
        val context: List<LogEntry>,
    )

    private val normalEntries = ArrayDeque<LogEntry>(MAX_NORMAL_ENTRIES)
    private val contextEntries = ArrayDeque<LogEntry>(MAX_CONTEXT_ENTRIES)
    private val errorRecords = ArrayList<ErrorRecord>()
    private val main = Handler(Looper.getMainLooper())
    private val writer = Executors.newSingleThreadExecutor { r ->
        Thread(r, "vpn-log-writer").apply { isDaemon = true }
    }
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile private var errorFile: File? = null
    @Volatile var listener: (() -> Unit)? = null

    private val pruneRunnable = object : Runnable {
        override fun run() {
            pruneExpired()
            main.postDelayed(this, PRUNE_INTERVAL_MS)
        }
    }

    @Synchronized
    fun init(context: Context) {
        if (errorFile != null) return
        val dir = File(context.applicationContext.filesDir, "diagnostics")
        if (!dir.exists()) dir.mkdirs()
        // Remove the old all-events journal: ordinary logs are no longer persisted.
        File(dir, "vpn-debug.log").delete()
        File(dir, "vpn-debug.log.1").delete()

        val file = File(dir, "vpn-errors.log")
        errorFile = file
        if (file.exists()) {
            try {
                file.forEachLine(Charsets.UTF_8) { line ->
                    decodeRecord(line)?.let { errorRecords.add(it) }
                }
            } catch (_: Exception) {
                // A damaged diagnostics file must never prevent VPN startup.
            }
        }
        pruneLocked(System.currentTimeMillis())
        persistErrorsAsync()
        main.removeCallbacks(pruneRunnable)
        main.postDelayed(pruneRunnable, PRUNE_INTERVAL_MS)
    }

    fun append(msg: String) {
        val now = System.currentTimeMillis()
        val severity = classify(msg)
        synchronized(this) {
            pruneLocked(now)
            val safeMessage = msg.take(MAX_MESSAGE_CHARS)
            val entry = LogEntry(now, severity, safeMessage)
            if (severity == LogSeverity.ERROR) {
                val context = contextEntries.filter { now - it.timestamp <= CONTEXT_RETENTION_MS }.takeLast(MAX_CONTEXT_PER_ERROR)
                // Avoid persisting the same critical error repeatedly within 30 seconds.
                val duplicate = errorRecords.lastOrNull()?.let {
                    it.entry.message == safeMessage && now - it.entry.timestamp < 30_000L
                } ?: false
                if (!duplicate) {
                    errorRecords.add(ErrorRecord(entry, context))
                    while (errorRecords.size > MAX_ERROR_RECORDS) errorRecords.removeAt(0)
                    persistErrorsAsync()
                }
            }

            normalEntries.addLast(entry)
            while (normalEntries.size > MAX_NORMAL_ENTRIES) normalEntries.removeFirst()
            contextEntries.addLast(entry)
            while (contextEntries.size > MAX_CONTEXT_ENTRIES) contextEntries.removeFirst()
            pruneLocked(now)
        }
        main.post { listener?.invoke() }
    }

    private fun classify(message: String): LogSeverity {
        val lower = message.lowercase(Locale.US)
        val critical = message.contains("[ERROR]", true) ||
            lower.contains("worker crashed") ||
            lower.contains("service start crashed") ||
            lower.contains("reconnect failed after") ||
            lower.contains("fatal tunnel error")
        if (critical) return LogSeverity.ERROR

        val warning = message.contains("[WARN]", true) ||
            lower.contains(" fail") || lower.contains("failed") ||
            lower.contains("dropped") || lower.contains("network lost") ||
            lower.contains("retry") || lower.contains("unavailable")
        return if (warning) LogSeverity.WARNING else LogSeverity.INFO
    }

    @Synchronized
    fun clear() {
        normalEntries.clear()
        contextEntries.clear()
        errorRecords.clear()
        writer.execute {
            try { errorFile?.delete() } catch (_: Exception) {}
        }
        main.post { listener?.invoke() }
    }

    fun pruneExpired() {
        var changed: Boolean
        synchronized(this) {
            changed = pruneLocked(System.currentTimeMillis())
        }
        if (changed) persistErrorsAsync()
        main.post { listener?.invoke() }
    }

    /** Returns the current one-hour view plus retained critical errors and their context. */
    @Synchronized
    fun entries(): List<LogEntry> {
        val now = System.currentTimeMillis()
        val changed = pruneLocked(now)
        if (changed) persistErrorsAsync()

        val merged = LinkedHashMap<String, LogEntry>()
        normalEntries.forEach { merged[key(it)] = it }
        errorRecords.forEach { record ->
            record.context.forEach { context ->
                val contextual = context.copy(isContext = true)
                merged.putIfAbsent(key(contextual), contextual)
            }
            merged[key(record.entry)] = record.entry
        }
        return merged.values.sortedBy { it.timestamp }
    }

    @Synchronized
    fun text(): String = entries().joinToString("\n") { format(it) }

    @Synchronized
    fun snapshot(): List<String> = entries().map { format(it) }

    fun format(entry: LogEntry): String {
        val stamp = synchronized(fmt) { fmt.format(Date(entry.timestamp)) }
        val tag = when {
            entry.isContext -> "CONTEXT"
            entry.severity == LogSeverity.ERROR -> "ERROR"
            entry.severity == LogSeverity.WARNING -> "WARN"
            else -> "INFO"
        }
        val message = if (entry.severity == LogSeverity.ERROR) {
            entry.message.replaceFirst(Regex("^\\s*\\[ERROR\\]\\s*", RegexOption.IGNORE_CASE), "")
        } else entry.message
        return "[$stamp] [$tag] $message"
    }

    private fun key(entry: LogEntry): String =
        "${entry.timestamp}|${entry.severity}|${entry.message}"

    /** Returns true when persisted error records were removed. */
    private fun pruneLocked(now: Long): Boolean {
        while (normalEntries.isNotEmpty() && now - normalEntries.first().timestamp > NORMAL_RETENTION_MS) {
            normalEntries.removeFirst()
        }
        while (contextEntries.isNotEmpty() && now - contextEntries.first().timestamp > CONTEXT_RETENTION_MS) {
            contextEntries.removeFirst()
        }
        val before = errorRecords.size
        errorRecords.removeAll { now - it.entry.timestamp > ERROR_RETENTION_MS }
        return before != errorRecords.size
    }

    private fun persistErrorsAsync() {
        val snapshot = synchronized(this) { errorRecords.toList() }
        writer.execute {
            try {
                val file = errorFile ?: return@execute
                val lines = snapshot.map { encodeRecord(it) }.toMutableList()
                while (lines.joinToString("\n").toByteArray(Charsets.UTF_8).size > MAX_ERROR_FILE_BYTES &&
                    lines.size > 1) {
                    lines.removeAt(0)
                }
                val temp = File(file.parentFile, "vpn-errors.log.tmp")
                temp.writeText(if (lines.isEmpty()) "" else lines.joinToString("\n") + "\n", Charsets.UTF_8)
                if (!temp.renameTo(file)) {
                    file.delete()
                    temp.renameTo(file)
                }
            } catch (_: Exception) {
                // Diagnostics must never crash or block the VPN service.
            }
        }
    }

    private fun encodeRecord(record: ErrorRecord): String {
        val message = encode(record.entry.message)
        val context = record.context.joinToString(";") {
            "${it.timestamp},${it.severity.name},${encode(it.message)}"
        }
        return "${record.entry.timestamp}|$message|${encode(context)}"
    }

    private fun decodeRecord(line: String): ErrorRecord? {
        return try {
            val parts = line.split("|", limit = 3)
            if (parts.size != 3) return null
            val timestamp = parts[0].toLong()
            val message = decode(parts[1])
            val contextText = decode(parts[2])
            val context = contextText.split(";").mapNotNull { item ->
                val fields = item.split(",", limit = 3)
                if (fields.size != 3) null
                else LogEntry(
                    timestamp = fields[0].toLong(),
                    severity = LogSeverity.valueOf(fields[1]),
                    message = decode(fields[2]),
                    isContext = true,
                )
            }
            ErrorRecord(LogEntry(timestamp, LogSeverity.ERROR, message), context)
        } catch (_: Exception) {
            null
        }
    }

    private fun encode(value: String): String =
        Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun decode(value: String): String =
        String(Base64.decode(value, Base64.NO_WRAP), Charsets.UTF_8)
}
