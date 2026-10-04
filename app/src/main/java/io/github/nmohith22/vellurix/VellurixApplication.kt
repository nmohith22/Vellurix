package io.github.nmohith22.vellurix

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.DateFormat
import java.util.Date

/** Temporary support diagnostics. Remove after the reader crash is identified and fixed. */
internal object ReaderDiagnostics {
    private const val PREFS = "temporary_reader_diagnostics"
    private const val REPORT = "vellurix-crash-report.txt"
    @Volatile private var format = "unknown"
    @Volatile private var stage = "application start"

    fun install(application: Application) {
        capturePreviousCrash(application)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { writeReport(application, "Uncaught application exception", thread.name, redactPaths(stackTrace(error))) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun mark(context: Context, nextStage: String, bookFormat: String? = null) {
        stage = nextStage
        if (bookFormat != null) format = bookFormat
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("stage", stage)
            .putString("format", format)
            .putLong("time", System.currentTimeMillis())
            .commit()
    }

    fun hasCrashReport(context: Context): Boolean = reportFile(context).isFile

    fun report(context: Context): String {
        reportFile(context).takeIf(File::isFile)?.let { return it.readText() }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return buildString {
            appendLine("Vellurix temporary diagnostic report")
            appendLine("No Java crash trace or Android process trace was available.")
            appendLine("App: ${appVersion(context)}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Last reader format: ${prefs.getString("format", "unknown")}")
            appendLine("Last reader stage: ${prefs.getString("stage", "not recorded")}")
            appendLine("Last reader attempt time: ${prefs.getLong("time", 0L).takeIf { it > 0L }?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "unknown"}")
        }
    }

    fun clear(context: Context) {
        reportFile(context).delete()
    }

    fun readerFailure(context: Context, failedAt: String, error: Throwable) {
        mark(context, failedAt)
        runCatching { writeReport(context, "Reader opening failure", Thread.currentThread().name, redactPaths(stackTrace(error))) }
    }

    private fun capturePreviousCrash(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || reportFile(context).exists()) return
        runCatching {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val previous = manager.getHistoricalProcessExitReasons(context.packageName, 0, 8)
                .firstOrNull { it.reason in setOf(ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE, ApplicationExitInfo.REASON_ANR) }
                ?: return
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (previous.timestamp <= prefs.getLong("captured_exit_time", 0L)) return
            val trace = previous.traceInputStream?.use(::readBoundedTrace)?.let(::redactPaths).orEmpty()
            val label = when (previous.reason) {
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "Previous native crash"
                ApplicationExitInfo.REASON_ANR -> "Previous app not responding event"
                else -> "Previous application crash"
            }
            writeReport(context, label, "system", buildString {
                appendLine("Android exit reason code: ${previous.reason}")
                previous.description?.takeIf(String::isNotBlank)?.let { appendLine("System detail: $it") }
                if (trace.isNotBlank()) appendLine(trace) else appendLine("Android did not provide a native crash trace.")
            }, previous.timestamp)
            prefs.edit().putLong("captured_exit_time", previous.timestamp).commit()
        }
    }

    private fun writeReport(context: Context, kind: String, threadName: String, trace: String, timestamp: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedFormat = prefs.getString("format", format) ?: format
        val savedStage = prefs.getString("stage", stage) ?: stage
        val version = appVersion(context)
        val folder = File(context.noBackupFilesDir, "diagnostics").apply { mkdirs() }
        File(folder, REPORT).writeText(buildString {
            appendLine("Vellurix temporary crash report")
            appendLine("Event: $kind")
            appendLine("Time: ${DateFormat.getDateTimeInstance().format(Date(timestamp))}")
            appendLine("App: $version")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Last reader format: ${savedFormat.take(16)}")
            appendLine("Last reader stage: ${savedStage.take(80)}")
            appendLine("Thread: ${threadName.take(80)}")
            appendLine()
            append(trace.take(256 * 1024))
        })
    }

    private fun reportFile(context: Context) = File(File(context.noBackupFilesDir, "diagnostics"), REPORT)

    private fun appVersion(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode})"
    }.getOrDefault("unknown")

    private fun stackTrace(error: Throwable): String = StringWriter().also { writer -> error.printStackTrace(PrintWriter(writer)) }.toString()

    private fun redactPaths(trace: String): String = trace
        .replace(Regex("content://[^\\s,)\\]}]+"), "content://[redacted]")
        .replace(Regex("file://[^\\s,)\\]}]+"), "file://[redacted]")
        .replace(Regex("(?i)\\b[A-Z]:\\\\Users\\\\[^\\s,)\\]}]+"), "[local path redacted]")
        .replace(Regex("/storage/[^\\s,)\\]}]+|/data/user/\\d+/[^\\s,)\\]}]+"), "[local path redacted]")

    private fun readBoundedTrace(input: java.io.InputStream): String {
        val bytes = ByteArray(256 * 1024)
        var count = 0
        while (count < bytes.size) {
            val read = input.read(bytes, count, bytes.size - count)
            if (read <= 0) break
            count += read
        }
        return String(bytes, 0, count, Charsets.UTF_8)
    }
}

class VellurixApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ReaderDiagnostics.install(this)
    }
}
