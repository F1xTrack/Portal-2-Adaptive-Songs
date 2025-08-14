package com.f1xtrack.portal2adaptivesongs

import android.content.Context
import android.location.Location
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RouteRecorder(private val context: Context) {
    private val file: File = File(context.filesDir, "routes_history.jsonl")
    private var sessionId: String? = null
    private var lastWriteMs: Long = 0L

    fun startSession() {
        if (sessionId != null) return
        val df = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        sessionId = df.format(Date())
        lastWriteMs = 0L
    }

    fun stopSession() {
        sessionId = null
    }

    fun addPoint(loc: Location, track: String, mode: String, speedKmh: Float) {
        val sid = sessionId ?: return
        val now = System.currentTimeMillis()
        // Throttle writes to at most once per 3 seconds
        if (now - lastWriteMs < 3000L) return
        lastWriteMs = now
        try {
            val obj = JSONObject()
                .put("t", now)
                .put("sid", sid)
                .put("lat", loc.latitude)
                .put("lon", loc.longitude)
                .put("alt", if (loc.hasAltitude()) loc.altitude else JSONObject.NULL)
                .put("track", track)
                .put("mode", mode)
                .put("speed", speedKmh)
            FileWriter(file, true).use { fw ->
                fw.append(obj.toString())
                fw.append('\n')
            }
        } catch (_: Exception) {
            // ignore
        }
    }

    companion object {
        fun readAll(context: Context): List<JSONObject> {
            val file = File(context.filesDir, "routes_history.jsonl")
            if (!file.exists()) return emptyList()
            val list = mutableListOf<JSONObject>()
            try {
                file.forEachLine { line ->
                    if (line.isNotBlank()) {
                        try { list += JSONObject(line) } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
            return list
        }

        fun clear(context: Context) {
            val file = File(context.filesDir, "routes_history.jsonl")
            if (file.exists()) file.delete()
        }
    }
}
