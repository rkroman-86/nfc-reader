package com.plugyourhome.nfcreader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedCode(
    val id: Long,
    val name: String,
    val value: String,
    val source: String,   // "UID", "Text", "URI", "Manual"…
)

/** Saved codes + emission settings, stored privately on the phone (excluded from backups). */
class CodeStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("codes", Context.MODE_PRIVATE)

    fun load(): List<SavedCode> {
        val arr = JSONArray(prefs.getString(KEY_LIST, "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SavedCode(o.getLong("id"), o.getString("name"), o.getString("value"), o.optString("source", "Manual"))
        }
    }

    fun save(list: List<SavedCode>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("value", it.value).put("source", it.source))
        }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply()
    }

    var activeId: Long
        get() = prefs.getLong(KEY_ACTIVE, -1L)
        set(v) = prefs.edit().putLong(KEY_ACTIVE, v).apply()

    var emitEnabled: Boolean
        get() = prefs.getBoolean(KEY_EMIT, false)
        set(v) = prefs.edit().putBoolean(KEY_EMIT, v).apply()

    fun activeCode(): SavedCode? = activeId.let { id -> load().firstOrNull { it.id == id } }

    companion object {
        private const val KEY_LIST = "list"
        private const val KEY_ACTIVE = "active"
        private const val KEY_EMIT = "emit"
        const val MAX_LEN = 200   // keeps the answer inside one APDU
    }
}
