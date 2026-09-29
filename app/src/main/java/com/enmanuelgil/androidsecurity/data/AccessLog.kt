package com.enmanuelgil.androidsecurity.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class Sensor(val label: String) { CAMERA("Cámara"), MICROPHONE("Micrófono") }

/**
 * Un uso de la cámara o el micrófono visto por el guardia mientras estaba activo.
 * Android NO dice qué app lo usó: [foregroundApp] es la app que estaba en pantalla en ese
 * momento (probable, no confirmado). Si era una app en segundo plano, no se puede saber.
 */
data class AccessEvent(
    val id           : Long,
    val sensor       : Sensor,
    val start        : Long,
    val end          : Long = 0L,          // 0 = sigue en uso
    val foregroundPkg: String? = null,
    val foregroundApp: String? = null,
    val note         : String = "",
)

/** Registro local (privado de la app, excluido de copias de seguridad). */
object AccessLog {
    private const val PREFS = "access_log_v3"
    private const val KEY = "events"
    private const val MAX = 500

    private val _events = MutableStateFlow<List<AccessEvent>>(emptyList())
    val events: StateFlow<List<AccessEvent>> = _events
    private var loaded = false

    @Synchronized
    fun load(context: Context): List<AccessEvent> {
        if (!loaded) {
            _events.value = deserialize(prefs(context).getString(KEY, "[]") ?: "[]")
            loaded = true
        }
        return _events.value
    }

    @Synchronized
    fun start(context: Context, sensor: Sensor, pkg: String?, app: String?, note: String = ""): Long {
        val now = System.currentTimeMillis()
        val list = load(context)
        // Si el mismo sensor se liberó hace un instante (la app lo reabre al pausar o salir), es el mismo uso
        list.firstOrNull { it.sensor == sensor }?.let { last ->
            if (last.end > 0 && now - last.end < 3000) {
                save(context, list.map { if (it.id == last.id) it.copy(end = 0L, note = mergeNotes(it.note, note)) else it })
                return last.id
            }
        }
        // id único aunque cámara y micrófono empiecen en el mismo milisegundo
        val id = maxOf(now, (list.maxOfOrNull { it.id } ?: 0L) + 1)
        val e = AccessEvent(id, sensor, now, 0L, pkg, app, note)
        save(context, (listOf(e) + list).take(MAX))
        return e.id
    }

    @Synchronized
    fun finish(context: Context, id: Long) {
        val now = System.currentTimeMillis()
        save(context, load(context).map { if (it.id == id && it.end == 0L) it.copy(end = now) else it })
    }

    /** Añade una nota a un uso abierto (p. ej. «la pantalla se apagó durante el uso»). */
    @Synchronized
    fun addNote(context: Context, id: Long, note: String) {
        save(context, load(context).map { if (it.id == id) it.copy(note = mergeNotes(it.note, note)) else it })
    }

    private fun mergeNotes(a: String, b: String): String =
        (a.split(" · ") + b.split(" · ")).map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(" · ")

    /** Al arrancar el guardia: los usos que quedaron abiertos (móvil apagado, app cerrada) se cierran sin duración. */
    @Synchronized
    fun closeDangling(context: Context) {
        val list = load(context)
        if (list.any { it.end == 0L }) save(context, list.map { if (it.end == 0L) it.copy(end = -1L) else it })
    }

    @Synchronized
    fun clear(context: Context) = save(context, emptyList())

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun save(context: Context, list: List<AccessEvent>) {
        _events.value = list
        prefs(context).edit().putString(KEY, serialize(list)).apply()
    }

    private fun serialize(list: List<AccessEvent>): String = JSONArray().apply {
        list.forEach { e ->
            put(JSONObject().apply {
                put("id", e.id); put("s", e.sensor.name); put("a", e.start); put("b", e.end)
                put("p", e.foregroundPkg ?: ""); put("n", e.foregroundApp ?: ""); put("note", e.note)
            })
        }
    }.toString()

    private fun deserialize(json: String): List<AccessEvent> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                AccessEvent(
                    id = o.getLong("id"), sensor = Sensor.valueOf(o.getString("s")),
                    start = o.getLong("a"), end = o.optLong("b", -1L),
                    foregroundPkg = o.optString("p").ifEmpty { null },
                    foregroundApp = o.optString("n").ifEmpty { null },
                    note = o.optString("note"),
                )
            }.getOrNull()
        }
    } catch (_: Exception) { emptyList() }
}
