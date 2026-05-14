package com.videooptimizer.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ExportManager {

    private const val PREFS_NAME = "export_history"
    private const val KEY_ITEMS = "items"

    fun getAll(context: Context): List<ExportedItem> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ExportedItem(
                    id = o.getString("id"),
                    originalName = o.getString("originalName"),
                    originalUriString = o.optString("originalUri", ""),
                    platform = o.getString("platform"),
                    uriString = o.getString("uri"),
                    originalFileSize = o.optLong("originalFileSize", 0L),
                    outputFileSize = o.getLong("fileSize"),
                    dateExported = o.getLong("date")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    fun add(context: Context, item: ExportedItem) {
        val items = getAll(context).toMutableList()
        items.add(0, item)
        save(context, items)
    }

    fun remove(context: Context, id: String) {
        save(context, getAll(context).filter { it.id != id })
    }

    private fun save(context: Context, items: List<ExportedItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("originalName", item.originalName)
                put("originalUri", item.originalUriString)
                put("platform", item.platform)
                put("uri", item.uriString)
                put("originalFileSize", item.originalFileSize)
                put("fileSize", item.outputFileSize)
                put("date", item.dateExported)
            })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_ITEMS, arr.toString()).apply()
    }
}
