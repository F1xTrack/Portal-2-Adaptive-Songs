package com.f1xtrack.portal2adaptivesongs

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

class StorageActivity : AppCompatActivity() {

    data class StorageItem(
        val name: String,
        val isUser: Boolean,
        val isHidden: Boolean
    )

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: StorageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_storage)

        // Верхняя панель удалена в разметке для минималистичного M3-экрана

        recycler = findViewById(R.id.recyclerStorage)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = StorageAdapter(emptyList(),
            onAction = { item ->
                if (item.isUser) confirmDeleteUser(item.name) else toggleHidden(item.name, item.isHidden)
            }
        )
        recycler.adapter = adapter

        findViewById<View>(R.id.btnDeleteAllUser).setOnClickListener { confirmDeleteAllUser() }
        findViewById<View>(R.id.btnUnhideAll).setOnClickListener { unhideAll() }
        findViewById<View>(R.id.btnClearStats).setOnClickListener { clearStats() }
        findViewById<View>(R.id.btnClearRoutes).setOnClickListener { confirmClearRoutes() }

        refreshList()
    }

    private fun assetFolderHasAnyVariant(folder: String, base: String): Boolean {
        return try {
            val files = assets.list(folder) ?: return false
            val regex = Regex("^${base}(\\d*)\\.wav$", RegexOption.IGNORE_CASE)
            files.any { regex.matches(it) }
        } catch (_: Exception) { false }
    }

    private fun userFolderHasAnyVariant(dir: File, base: String): Boolean {
        if (!dir.exists() || !dir.isDirectory) return false
        val regex = Regex("^${base}(\\d*)\\.wav$", RegexOption.IGNORE_CASE)
        return dir.listFiles()?.any { file -> regex.matches(file.name) } == true
    }

    private fun getHiddenAssets(): MutableSet<String> {
        val prefs = getSharedPreferences("storage_prefs", MODE_PRIVATE)
        return prefs.getStringSet("hidden_assets", emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    private fun setHiddenAssets(set: Set<String>) {
        val prefs = getSharedPreferences("storage_prefs", MODE_PRIVATE)
        prefs.edit().putStringSet("hidden_assets", set).apply()
    }

    private fun buildItems(): List<StorageItem> {
        val hidden = getHiddenAssets()
        val assetTracks = assets.list("")?.filter { name ->
            assetFolderHasAnyVariant(name, "normal") && assetFolderHasAnyVariant(name, "superspeed")
        } ?: emptyList()
        val userDir = File(filesDir, "soundtracks")
        val userTracks = userDir.listFiles()?.filter { dir ->
            dir.isDirectory && userFolderHasAnyVariant(dir, "normal") && userFolderHasAnyVariant(dir, "superspeed")
        }?.map { it.name } ?: emptyList()
        val items = mutableListOf<StorageItem>()
        items += assetTracks.map { StorageItem(it, isUser = false, isHidden = hidden.contains(it)) }
        items += userTracks.map { StorageItem(it, isUser = true, isHidden = false) }
        return items.sortedBy { it.name.lowercase() }
    }

    private fun refreshList() {
        adapter.update(buildItems())
    }

    private fun toggleHidden(name: String, currentlyHidden: Boolean) {
        val hidden = getHiddenAssets()
        if (currentlyHidden) hidden.remove(name) else hidden.add(name)
        setHiddenAssets(hidden)
        Toast.makeText(this, if (currentlyHidden) getString(R.string.storage_unhide) else getString(R.string.storage_hide), Toast.LENGTH_SHORT).show()
        refreshList()
    }

    private fun confirmDeleteUser(name: String) {
        MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.storage_confirm_delete_track, name))
            .setPositiveButton(R.string.storage_delete) { _, _ -> deleteUserTrack(name) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteUserTrack(name: String) {
        val dir = File(filesDir, "soundtracks/$name")
        if (dir.exists()) dir.deleteRecursively()
        // Purge stats entries for this track
        val stats = getSharedPreferences("track_stats", MODE_PRIVATE)
        val play = stats.getStringSet("play_counts", emptySet())?.toMutableSet() ?: mutableSetOf()
        val dur = stats.getStringSet("durations", emptySet())?.toMutableSet() ?: mutableSetOf()
        play.removeAll { it.substringBefore('|') == name }
        dur.removeAll { it.substringBefore('|') == name }
        stats.edit().putStringSet("play_counts", play).putStringSet("durations", dur).apply()
        Toast.makeText(this, getString(R.string.storage_delete), Toast.LENGTH_SHORT).show()
        refreshList()
    }

    private fun confirmDeleteAllUser() {
        MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.storage_confirm_delete_all_user))
            .setPositiveButton(R.string.storage_delete_all_user) { _, _ -> deleteAllUser() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteAllUser() {
        val root = File(filesDir, "soundtracks")
        if (root.exists()) {
            root.listFiles()?.forEach { if (it.isDirectory) it.deleteRecursively() }
        }
        // Purge stats for all user tracks (any track that is not in assets list)
        val assetNames = assets.list("")?.toSet() ?: emptySet()
        val stats = getSharedPreferences("track_stats", MODE_PRIVATE)
        val play = stats.getStringSet("play_counts", emptySet())?.toMutableSet() ?: mutableSetOf()
        val dur = stats.getStringSet("durations", emptySet())?.toMutableSet() ?: mutableSetOf()
        play.removeAll { it.substringBefore('|') !in assetNames }
        dur.removeAll { it.substringBefore('|') !in assetNames }
        stats.edit().putStringSet("play_counts", play).putStringSet("durations", dur).apply()
        Toast.makeText(this, getString(R.string.storage_delete_all_user), Toast.LENGTH_SHORT).show()
        refreshList()
    }

    private fun unhideAll() {
        setHiddenAssets(emptySet())
        Toast.makeText(this, getString(R.string.storage_unhide_all), Toast.LENGTH_SHORT).show()
        refreshList()
    }

    private fun clearStats() {
        val prefs = getSharedPreferences("track_stats", MODE_PRIVATE)
        // Очищаем статистические данные, кроме количества треков (ключей для количества нет — хранимые наборы play_counts и durations)
        prefs.edit().remove("play_counts").remove("durations").apply()
        Toast.makeText(this, getString(R.string.storage_clear_stats), Toast.LENGTH_SHORT).show()
    }

    private fun confirmClearRoutes() {
        MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.storage_confirm_clear_routes))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                RouteRecorder.clear(this)
                Toast.makeText(this, getString(R.string.storage_done), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
