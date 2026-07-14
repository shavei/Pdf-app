package com.pdfapp.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** One entry on the home screen's recently-opened list. */
data class RecentFile(
    val uri: String,
    val displayName: String,
    val pageCount: Int,
    val lastPageIndex: Int,
    val lastOpenedEpochMillis: Long,
    /** App-private PNG of page 1, or null when none was captured. */
    val thumbnailPath: String? = null,
)

/** JSON (de)serialisation for the recents list persisted in DataStore. */
object RecentFilesCodec {
    fun encode(files: List<RecentFile>): String {
        val array = JSONArray()
        files.forEach { file ->
            array.put(
                JSONObject()
                    .put("uri", file.uri)
                    .put("name", file.displayName)
                    .put("pages", file.pageCount)
                    .put("lastPage", file.lastPageIndex)
                    .put("openedAt", file.lastOpenedEpochMillis)
                    .putOpt("thumb", file.thumbnailPath),
            )
        }
        return array.toString()
    }

    /** Decode [json]; malformed input yields an empty list, never a crash. */
    fun decode(json: String): List<RecentFile> =
        try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                RecentFile(
                    uri = item.getString("uri"),
                    displayName = item.getString("name"),
                    pageCount = item.getInt("pages"),
                    lastPageIndex = item.getInt("lastPage"),
                    lastOpenedEpochMillis = item.getLong("openedAt"),
                    thumbnailPath = item.optString("thumb").takeIf { it.isNotEmpty() },
                )
            }
        } catch (_: JSONException) {
            emptyList()
        }
}
