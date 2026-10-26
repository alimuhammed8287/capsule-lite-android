package com.example.capsule

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// A diagnostics log kept in a plain file, so it survives a crash, a force-stop, or a reboot.
// The settings screen shows it and can copy all of it.
object DiagLog {
    private const val FILE = "capsule-diag.log"
    private const val MAX_BYTES = 256 * 1024
    private const val KEEP_BYTES = 160 * 1024
    @Volatile private var crashHandlerSet = false

    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, FILE)

    private fun stamp(ms: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(ms))

    @Synchronized
    fun add(ctx: Context, msg: String) {
        runCatching {
            val f = file(ctx)
            f.appendText("${stamp()} $msg\n")
            if (f.length() > MAX_BYTES) {
                val text = f.readText()
                f.writeText(text.takeLast(KEEP_BYTES).substringAfter('\n'))
            }
        }
    }

    @Synchronized
    fun read(ctx: Context): String =
        runCatching { file(ctx).takeIf { it.exists() }?.readText() ?: "" }.getOrDefault("")

    @Synchronized
    fun clear(ctx: Context) {
        runCatching { file(ctx).delete() }
    }

    fun lastLines(ctx: Context, n: Int): String =
        read(ctx).trimEnd().lines().takeLast(n).joinToString("\n")

    // Writes any crash to the log before the process dies
    fun installCrashHandler(ctx: Context) {
        if (crashHandlerSet) return
        crashHandlerSet = true
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            add(
                app,
                "CRASH in thread ${t.name}: ${e.javaClass.name}: ${e.message}\n" +
                    e.stackTraceToString().lines().take(14).joinToString("\n") { "    $it" }
            )
            previous?.uncaughtException(t, e)
        }
    }

    private fun reasonName(r: Int): String = when (r) {
        0 -> "UNKNOWN"
        1 -> "EXIT_SELF"
        2 -> "SIGNALED"
        3 -> "LOW_MEMORY"
        4 -> "CRASH"
        5 -> "CRASH_NATIVE"
        6 -> "ANR"
        7 -> "INITIALIZATION_FAILURE"
        8 -> "PERMISSION_CHANGE"
        9 -> "EXCESSIVE_RESOURCE_USAGE"
        10 -> "USER_REQUESTED"
        11 -> "USER_STOPPED"
        12 -> "DEPENDENCY_DIED"
        13 -> "OTHER"
        14 -> "FREEZER"
        15 -> "PACKAGE_STATE_CHANGE"
        16 -> "PACKAGE_UPDATED"
        else -> "reason $r"
    }

    // Android remembers why this app's earlier processes ended. Log it, so a silent death leaves a reason.
    fun logExitReasons(ctx: Context) {
        if (Build.VERSION.SDK_INT < 30) return
        runCatching {
            val prefs = ctx.getSharedPreferences("capsule", Context.MODE_PRIVATE)
            val seen = prefs.getLong("exitSeen", 0L)
            val am = ctx.getSystemService(ActivityManager::class.java)
            val list = am.getHistoricalProcessExitReasons(ctx.packageName, 0, 6)
            var newest = seen
            for (i in list.sortedBy { it.timestamp }) {
                if (i.timestamp <= seen) continue
                add(
                    ctx,
                    "earlier process ended at ${stamp(i.timestamp)}: ${reasonName(i.reason)}, " +
                        "importance ${i.importance}, status ${i.status}, ${i.description ?: ""}"
                )
                if (i.timestamp > newest) newest = i.timestamp
            }
            if (newest > seen) prefs.edit().putLong("exitSeen", newest).apply()
        }
    }

    // Everything needed to understand a problem, as one piece of text for the clipboard
    fun report(ctx: Context): String {
        val sb = StringBuilder()
        sb.append("Capsule Lite diagnostics, ${stamp()}\n")
        sb.append(
            "device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), " +
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.DISPLAY}\n"
        )
        sb.append(KeepAlive.status(ctx))
        sb.append("\n--- settings ---\n")
        val prefs = ctx.getSharedPreferences("capsule", Context.MODE_PRIVATE)
        prefs.all.entries.sortedBy { it.key }
            .filter { it.key != "diag" && it.key != "testPill" }
            .forEach { sb.append("${it.key} = ${it.value}\n") }
        sb.append("\n--- log (oldest first) ---\n")
        sb.append(read(ctx).takeLast(150_000))
        return sb.toString()
    }
}
