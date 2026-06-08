package com.enmanuelgil.androidsecurity.data

import android.content.Context
import com.enmanuelgil.androidsecurity.model.SensorType
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent log of camera / microphone access events detected by CamMicGuardService.
 * Stored in SharedPreferences as JSON. Only contains events actually observed by the
 * running service — no false positives from permission lists or usage stats.
 */
data class AccessEvent(
    val packageName : String,
    val appName     : String,
    val sensorType  : SensorType,
    val timestamp   : Long,
    val durationMs  : Long   = 0L,
    val note        : String = ""
)

object AccessLog {

    private const val PREFS      = "access_log_v2"
    private const val KEY        = "events"
    private const val MAX_EVENTS = 300

    fun add(context: Context, event: AccessEvent) {
        val current = readAll(context).toMutableList()
        // Deduplicate: skip if same package+sensor in last 10 seconds
        val tooRecent = current.firstOrNull()
            ?.let { it.packageName == event.packageName &&
                    it.sensorType == event.sensorType &&
                    (event.timestamp - it.timestamp) < 10_000L }
            ?: false
        if (tooRecent) return

        current.add(0, event)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, serialize(current.take(MAX_EVENTS)))
            .apply()
    }

    fun readAll(context: Context): List<AccessEvent> =
        deserialize(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]")

    fun readInWindow(context: Context, windowMs: Long): List<AccessEvent> {
        val cutoff = System.currentTimeMillis() - windowMs
        return readAll(context).filter { it.timestamp >= cutoff }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    // ── Serialization ──────────────────────────────────
    private fun serialize(list: List<AccessEvent>): String {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().apply {
                put("pkg",    e.packageName)
                put("name",   e.appName)
                put("sensor", e.sensorType.name)
                put("ts",     e.timestamp)
                put("dur",    e.durationMs)
                put("note",   e.note)
            })
        }
        return arr.toString()
    }

    private fun deserialize(json: String): List<AccessEvent> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                AccessEvent(
                    packageName = o.getString("pkg"),
                    appName     = o.getString("name"),
                    sensorType  = SensorType.valueOf(o.getString("sensor")),
                    timestamp   = o.getLong("ts"),
                    durationMs  = o.getLong("dur"),
                    note        = o.optString("note", "")
                )
            }.getOrNull()
        }
    } catch (e: Exception) { emptyList() }
}
