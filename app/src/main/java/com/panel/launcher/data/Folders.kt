package com.panel.launcher.data

import org.json.JSONArray
import org.json.JSONObject

data class Folder(val id: String, val name: String, val apps: List<String>)

/** Folders are kept as a small JSON object: { id: { "name": ..., "apps": [keys] } }. */
object Folders {
    fun load(json: String): Map<String, Folder> = runCatching {
        val o = JSONObject(json)
        o.keys().asSequence().associateWith { id ->
            val f = o.getJSONObject(id)
            val arr = f.optJSONArray("apps") ?: JSONArray()
            Folder(id, f.optString("name", "Folder"), (0 until arr.length()).map { arr.getString(it) })
        }
    }.getOrDefault(emptyMap())

    fun save(map: Map<String, Folder>): String {
        val o = JSONObject()
        map.values.forEach { f -> o.put(f.id, JSONObject().put("name", f.name).put("apps", JSONArray(f.apps))) }
        return o.toString()
    }
}
