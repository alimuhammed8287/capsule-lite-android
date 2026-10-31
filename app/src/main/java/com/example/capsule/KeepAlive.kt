package com.example.capsule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService

// Helps Capsule stay alive. Android phones, HyperOS especially, can stop a background service or switch an
// accessibility service off. This keeps a record of it, asks Android to reconnect, and, if the user allowed
// it once with a single adb command, switches the accessibility service back on by itself.
object KeepAlive {
    const val ACTION = "com.example.capsulelite.KEEPALIVE"
    private const val ALARM_REQUEST = 4711
    private const val REPAIR_PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"
    private const val ACCESSIBILITY_REPAIR_PENDING = "accessibilityRepairPending"
    private const val LISTENER_REPAIR_PENDING = "listenerRepairPending"
    private const val RECENT_BIND_GRACE_MS = 60_000L
    private const val REPAIR_RETRY_COOLDOWN_MS = 5 * 60_000L

    @Volatile var listenerConnected = false
    @Volatile var accessibilityConnected = false

    fun markListenerConnected(ctx: Context) {
        listenerConnected = true
        ctx.getSharedPreferences("capsule", Context.MODE_PRIVATE).edit()
            .putLong("lastListenerConnectedAt", SystemClock.elapsedRealtime()).apply()
    }

    fun markAccessibilityConnected(ctx: Context) {
        accessibilityConnected = true
        ctx.getSharedPreferences("capsule", Context.MODE_PRIVATE).edit()
            .putLong("lastAccessibilityConnectedAt", SystemClock.elapsedRealtime()).apply()
    }

    private fun enabled(ctx: Context, key: String): List<ComponentName> =
        (Settings.Secure.getString(ctx.contentResolver, key) ?: "")
            .split(':')
            .mapNotNull { ComponentName.unflattenFromString(it) }

    fun isListenerEnabled(ctx: Context): Boolean =
        ComponentName(ctx, CapsuleService::class.java) in enabled(ctx, "enabled_notification_listeners")

    fun isAccessibilityEnabled(ctx: Context): Boolean =
        ComponentName(ctx, CapsuleWatchdog::class.java) in
            enabled(ctx, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

    fun canSelfRepair(ctx: Context): Boolean =
        ctx.checkSelfPermission(REPAIR_PERMISSION) == PackageManager.PERMISSION_GRANTED

    private fun autoRepairRequested(ctx: Context): Boolean =
        ctx.getSharedPreferences("capsule", Context.MODE_PRIVATE)
            .getBoolean("autoRepairServices", false)

    private fun secureList(ctx: Context, key: String): MutableList<String> =
        (Settings.Secure.getString(ctx.contentResolver, key) ?: "")
            .split(':').map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()

    fun repairPending(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences("capsule", Context.MODE_PRIVATE)
        return prefs.getBoolean(ACCESSIBILITY_REPAIR_PENDING, false) ||
            prefs.getBoolean(LISTENER_REPAIR_PENDING, false)
    }

    private fun recentlyConnected(ctx: Context, key: String): Boolean {
        val last = ctx.getSharedPreferences("capsule", Context.MODE_PRIVATE).getLong(key, 0L)
        val age = SystemClock.elapsedRealtime() - last
        return last > 0L && age in 0..RECENT_BIND_GRACE_MS
    }

    private fun repairCooldownActive(ctx: Context, key: String): Boolean {
        val last = ctx.getSharedPreferences("capsule", Context.MODE_PRIVATE).getLong(key, 0L)
        val age = SystemClock.elapsedRealtime() - last
        return last > 0L && age in 0..REPAIR_RETRY_COOLDOWN_MS
    }

    fun adbCommand(ctx: Context): String = "adb shell pm grant ${ctx.packageName} $REPAIR_PERMISSION"

    // Service repair is deliberately opt-in: an ADB grant alone must not override a user's later choice.
    fun ensureAccessibility(ctx: Context): String {
        val prefs = ctx.getSharedPreferences("capsule", Context.MODE_PRIVATE)
        val flat = ComponentName(ctx, CapsuleWatchdog::class.java).flattenToString()
        val pending = prefs.getBoolean(ACCESSIBILITY_REPAIR_PENDING, false)
        val enabled = isAccessibilityEnabled(ctx)
        if (enabled && accessibilityConnected && !pending) return "on"
        if (enabled && !accessibilityConnected && !pending && recentlyConnected(ctx, "lastAccessibilityConnectedAt")) {
            return "enabled but disconnected; waiting for Android to bind"
        }
        if (enabled && !accessibilityConnected && !pending && repairCooldownActive(ctx, "lastAccessibilityRepairAt")) {
            return "enabled but disconnected; reconnect pending"
        }
        if (!pending && !autoRepairRequested(ctx)) return if (enabled) "enabled, disconnected (automatic repair is off)" else
            "OFF (automatic repair disabled in settings)"
        if (!canSelfRepair(ctx)) {
            if (pending) prefs.edit().remove(ACCESSIBILITY_REPAIR_PENDING).commit()
            return if (enabled) "enabled, disconnected (self-repair permission missing)" else
                "OFF (self-repair permission not given)"
        }
        return runCatching {
            val services = secureList(ctx, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            if (pending) {
                if (flat !in services) services.add(flat)
                val saved = Settings.Secure.putString(
                    ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, services.joinToString(":")
                )
                val globallyEnabled = Settings.Secure.putInt(ctx.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                prefs.edit().remove(ACCESSIBILITY_REPAIR_PENDING)
                    .putLong("lastAccessibilityRepairAt", SystemClock.elapsedRealtime()).commit()
                if (saved && globallyEnabled && isAccessibilityEnabled(ctx)) {
                    "disconnected; accessibility service restarted, reconnect pending"
                } else "OFF (Android did not confirm accessibility recovery)"
            } else if (enabled && !accessibilityConnected) {
                // HyperOS can leave a service listed as enabled after its binding has died.
                // Cycle only Capsule's entry; preserve every other enabled accessibility service.
                val remaining = services.filterNot { it == flat }
                val saved = Settings.Secure.putString(
                    ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, remaining.joinToString(":")
                )
                if (!saved) return@runCatching "enabled but disconnected (Android refused the restart)"
                if (remaining.isEmpty()) {
                    Settings.Secure.putInt(ctx.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
                } else {
                    Settings.Secure.putInt(ctx.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                }
                prefs.edit().putBoolean(ACCESSIBILITY_REPAIR_PENDING, true).commit()
                "enabled but disconnected; restart staged"
            } else {
                if (flat !in services) services.add(flat)
                val saved = Settings.Secure.putString(
                    ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, services.joinToString(":")
                )
                val globallyEnabled = Settings.Secure.putInt(ctx.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                if (saved && globallyEnabled && isAccessibilityEnabled(ctx)) "was OFF, switched back on"
                else "OFF (repair attempted; Android has not confirmed the service is enabled)"
            }
        }.getOrElse { "OFF, repair failed: ${it.javaClass.simpleName}: ${it.message}" }
    }

    // Same for the notification listener; only repair when the user explicitly enabled the option.
    fun ensureListener(ctx: Context): String {
        val prefs = ctx.getSharedPreferences("capsule", Context.MODE_PRIVATE)
        val flat = ComponentName(ctx, CapsuleService::class.java).flattenToString()
        val pending = prefs.getBoolean(LISTENER_REPAIR_PENDING, false)
        val enabled = isListenerEnabled(ctx)
        if (enabled && listenerConnected && !pending) return "on"
        if (enabled && !listenerConnected && !pending && recentlyConnected(ctx, "lastListenerConnectedAt")) {
            NotificationListenerService.requestRebind(ComponentName(ctx, CapsuleService::class.java))
            return "enabled but disconnected; rebind requested"
        }
        if (enabled && !listenerConnected && !pending && repairCooldownActive(ctx, "lastListenerRepairAt")) {
            NotificationListenerService.requestRebind(ComponentName(ctx, CapsuleService::class.java))
            return "enabled but disconnected; reconnect pending"
        }
        if (!pending && !autoRepairRequested(ctx)) return if (enabled) "enabled, disconnected (automatic repair is off)" else
            "OFF (automatic repair disabled in settings)"
        if (!canSelfRepair(ctx)) {
            if (pending) prefs.edit().remove(LISTENER_REPAIR_PENDING).commit()
            return if (enabled) "enabled, disconnected (self-repair permission missing)" else
                "OFF (self-repair permission not given)"
        }
        return runCatching {
            val services = secureList(ctx, "enabled_notification_listeners")
            if (pending) {
                if (flat !in services) services.add(flat)
                val saved = Settings.Secure.putString(
                    ctx.contentResolver, "enabled_notification_listeners", services.joinToString(":")
                )
                prefs.edit().remove(LISTENER_REPAIR_PENDING)
                    .putLong("lastListenerRepairAt", SystemClock.elapsedRealtime()).commit()
                if (saved && isListenerEnabled(ctx)) {
                    NotificationListenerService.requestRebind(ComponentName(ctx, CapsuleService::class.java))
                    "disconnected; notification listener restarted, reconnect pending"
                } else "OFF (Android did not confirm notification-listener recovery)"
            } else if (enabled && !listenerConnected) {
                val remaining = services.filterNot { it == flat }
                val saved = Settings.Secure.putString(
                    ctx.contentResolver, "enabled_notification_listeners", remaining.joinToString(":")
                )
                if (!saved) return@runCatching "enabled but disconnected (Android refused the restart)"
                prefs.edit().putBoolean(LISTENER_REPAIR_PENDING, true).commit()
                "enabled but disconnected; restart staged"
            } else {
                if (flat !in services) services.add(flat)
                val saved = Settings.Secure.putString(
                    ctx.contentResolver, "enabled_notification_listeners", services.joinToString(":")
                )
                if (!saved || !isListenerEnabled(ctx)) "OFF (repair attempted; Android has not confirmed the listener is enabled)"
                else {
                    NotificationListenerService.requestRebind(ComponentName(ctx, CapsuleService::class.java))
                    "was OFF, switched back on"
                }
            }
        }.getOrElse { "OFF, repair failed: ${it.javaClass.simpleName}: ${it.message}" }
    }

    fun schedule(ctx: Context, delayMs: Long = 15 * 60 * 1000L) {
        runCatching {
            val am = ctx.getSystemService(AlarmManager::class.java)
            val pi = PendingIntent.getBroadcast(
                ctx, ALARM_REQUEST,
                Intent(ctx, KeepAliveReceiver::class.java).setAction(ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delayMs, pi)
        }.onFailure {
            // Keep-alive is best-effort on OEM builds; record a failure instead of silently hiding it.
            DiagLog.add(ctx, "keep-alive alarm scheduling failed: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    fun status(ctx: Context): String {
        val pm = ctx.getSystemService(PowerManager::class.java)
        val prefs = ctx.getSharedPreferences("capsule", Context.MODE_PRIVATE)
        return buildString {
            append("overlay permission: ").append(Settings.canDrawOverlays(ctx)).append('\n')
            append("notification listener: enabled=").append(isListenerEnabled(ctx))
                .append(", connected=").append(listenerConnected).append('\n')
            append("accessibility keep-alive: enabled=").append(isAccessibilityEnabled(ctx))
                .append(", connected=").append(accessibilityConnected).append('\n')
            append("battery optimisation ignored: ").append(pm.isIgnoringBatteryOptimizations(ctx.packageName)).append('\n')
            append("self-repair permission given: ").append(canSelfRepair(ctx)).append('\n')
            append("automatic service repair opted in: ").append(prefs.getBoolean("autoRepairServices", false)).append('\n')
            append("keep-running notification: ").append(prefs.getBoolean("keepNotif", true)).append('\n')
        }
    }
}

// Wakes up now and then, even if the app's process was stopped, writes a line to the log,
// asks Android to reconnect the notification listener, and repairs accessibility if allowed.
class KeepAliveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        var acc = ""
        var lis = ""
        runCatching {
            DiagLog.installCrashHandler(context)
            acc = KeepAlive.ensureAccessibility(context)
            lis = KeepAlive.ensureListener(context)
            DiagLog.add(
                context,
                "keep-alive check: listener=$lis (connected=${KeepAlive.listenerConnected}), accessibility=$acc " +
                    "(connected=${KeepAlive.accessibilityConnected})"
            )
            if (KeepAlive.isListenerEnabled(context)) {
                NotificationListenerService.requestRebind(ComponentName(context, CapsuleService::class.java))
            }
        }
        val retryDelay = when {
            KeepAlive.repairPending(context) -> 2_000L
            acc.contains("waiting for Android") || lis.contains("rebind requested") -> 30_000L
            acc.contains("reconnect pending") || lis.contains("reconnect pending") -> 30_000L
            else -> 15 * 60 * 1000L
        }
        KeepAlive.schedule(context, retryDelay)
    }
}
