package com.example.capsule

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small, on-device charging-session summaries. We keep aggregates only, never a sample-by-sample
 * telemetry stream, and cap completed sessions to avoid unbounded storage.
 */
internal object ChargingSessionHistory {
    private const val PREFS = "capsule_charging_history"
    private const val ACTIVE = "active_session"
    private const val COMPLETED = "completed_sessions"
    private const val MAX_SESSIONS = 20

    @Synchronized
    fun record(
        context: Context,
        charging: Boolean,
        percent: Int,
        temperatureC: Double,
        voltageV: Double,
        currentA: Double?,
        watts: Double?
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val active = prefs.getString(ACTIVE, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (!charging) {
            if (active != null) {
                active.put("endedAt", System.currentTimeMillis())
                active.put("endPercent", percent.coerceIn(0, 100))
                val history = runCatching { JSONArray(prefs.getString(COMPLETED, "[]")) }.getOrElse { JSONArray() }
                history.put(0, active)
                val bounded = JSONArray()
                for (index in 0 until minOf(history.length(), MAX_SESSIONS)) bounded.put(history.getJSONObject(index))
                prefs.edit().putString(COMPLETED, bounded.toString()).remove(ACTIVE).apply()
            }
            return
        }

        val now = System.currentTimeMillis()
        val session = active ?: JSONObject()
            .put("startedAt", now)
            .put("startPercent", percent.coerceIn(0, 100))
            .put("minTempC", temperatureC)
            .put("maxTempC", temperatureC)
            .put("wattsSum", 0.0)
            .put("wattsSamples", 0)
            .put("peakWatts", 0.0)
        session.put("endPercent", percent.coerceIn(0, 100))
        session.put("minTempC", minOf(session.optDouble("minTempC", temperatureC), temperatureC))
        session.put("maxTempC", maxOf(session.optDouble("maxTempC", temperatureC), temperatureC))
        if (voltageV > 0.0) session.put("latestVoltageV", voltageV)
        if (currentA != null && currentA > 0.0) session.put("latestCurrentA", currentA)
        if (watts != null && watts > 0.0) {
            session.put("wattsSum", session.optDouble("wattsSum", 0.0) + watts)
            session.put("wattsSamples", session.optInt("wattsSamples", 0) + 1)
            session.put("peakWatts", maxOf(session.optDouble("peakWatts", 0.0), watts))
        }
        prefs.edit().putString(ACTIVE, session.toString()).apply()
    }

    @Synchronized
    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(COMPLETED).apply()
    }

    fun summary(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val active = prefs.getString(ACTIVE, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        val sessions = runCatching { JSONArray(prefs.getString(COMPLETED, "[]")) }.getOrElse { JSONArray() }
        if (active == null && sessions.length() == 0) {
            return "No charging sessions recorded yet. Capsule records summaries while its service is running."
        }
        val lines = mutableListOf<String>()
        if (active != null) lines += format(active, ongoing = true)
        for (index in 0 until minOf(sessions.length(), 5)) {
            sessions.optJSONObject(index)?.let { lines += format(it, ongoing = false) }
        }
        return lines.joinToString("\n\n")
    }

    private fun format(session: JSONObject, ongoing: Boolean): String {
        val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        val startedAt = session.optLong("startedAt", 0L)
        val endedAt = if (ongoing) System.currentTimeMillis() else session.optLong("endedAt", startedAt)
        val minutes = ((endedAt - startedAt).coerceAtLeast(0L) / 60_000L)
        val startPct = session.optInt("startPercent", 0)
        val endPct = session.optInt("endPercent", startPct)
        val sampleCount = session.optInt("wattsSamples", 0)
        val averageWatts = if (sampleCount > 0) session.optDouble("wattsSum", 0.0) / sampleCount else 0.0
        val power = if (sampleCount > 0) {
            "avg %.1f W · peak %.1f W".format(Locale.getDefault(), averageWatts, session.optDouble("peakWatts", 0.0))
        } else {
            "power readings unavailable"
        }
        return buildString {
            append(if (ongoing) "Charging now" else "Last session")
            append(" · ").append(if (startedAt > 0L) dateFormat.format(Date(startedAt)) else "time unavailable").append('\n')
            append(startPct).append("% → ").append(endPct).append("% · ")
                .append(if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m")
                .append(" · ").append(power).append('\n')
            append("Temperature ").append(
                "%.1f–%.1f°C".format(
                    Locale.getDefault(),
                    session.optDouble("minTempC", 0.0),
                    session.optDouble("maxTempC", 0.0)
                )
            )
        }
    }
}
