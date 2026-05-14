package com.videooptimizer.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object HistoryManager {

    private const val PREFS_NAME = "video_history"
    private const val KEY_ITEMS = "items"

    fun getAll(context: Context): List<HistoryItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ITEMS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                HistoryItem(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    uriString = obj.getString("uri"),
                    dateAdded = obj.getLong("date")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(context: Context, item: HistoryItem) {
        val items = getAll(context).toMutableList()
        items.add(0, item)
        save(context, items)
    }

    fun remove(context: Context, id: String) {
        save(context, getAll(context).filter { it.id != id })
    }

    private fun save(context: Context, items: List<HistoryItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("uri", item.uriString)
                put("date", item.dateAdded)
            })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_ITEMS, arr.toString()).apply()
    }
}
