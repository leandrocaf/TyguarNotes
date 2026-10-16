package com.lcaf.tyguarnotes.data.local

import com.lcaf.tyguarnotes.ui.data.ChecklistItem
import org.json.JSONArray
import org.json.JSONObject

/** Checklist items as a JSON array, as stored in the database and in synced records. */
object ChecklistCodec {

    fun toJson(items: List<ChecklistItem>): JSONArray = JSONArray().apply {
        items.forEach { put(JSONObject().put("id", it.id).put("text", it.text).put("checked", it.checked)) }
    }

    fun fromJson(array: JSONArray?): List<ChecklistItem> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            ChecklistItem(
                id = item.optString("id").ifEmpty { "item_$index" },
                text = item.optString("text"),
                checked = item.optBoolean("checked")
            )
        }
    }

    fun encode(items: List<ChecklistItem>): String = toJson(items).toString()

    fun decode(json: String): List<ChecklistItem> =
        if (json.isBlank()) emptyList() else fromJson(runCatching { JSONArray(json) }.getOrNull())
}
