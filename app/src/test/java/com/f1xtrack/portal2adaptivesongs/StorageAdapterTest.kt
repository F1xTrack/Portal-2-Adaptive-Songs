package com.f1xtrack.portal2adaptivesongs

import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StorageAdapterTest {

    @Test
    fun `binds imported track with delete action`() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val item = StorageActivity.StorageItem(name = "Custom Pack", isUser = true, isHidden = false)
        var clicked: StorageActivity.StorageItem? = null

        val adapter = StorageAdapter(listOf(item)) { clicked = it }
        val holder = adapter.onCreateViewHolder(FrameLayout(activity), 0)

        adapter.onBindViewHolder(holder, 0)
        holder.action.performClick()

        val importedLabel = activity.getString(R.string.storage_imported_label)
        assertEquals("Custom Pack", holder.name.text.toString())
        assertEquals(
            activity.getString(R.string.storage_item_info_plain, importedLabel),
            holder.info.text.toString()
        )
        assertEquals(activity.getString(R.string.storage_delete), holder.action.text.toString())
        assertEquals(item, clicked)
    }

    @Test
    fun `binds hidden built in track with unhide action`() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val item = StorageActivity.StorageItem(name = "Portal OST", isUser = false, isHidden = true)

        val adapter = StorageAdapter(listOf(item)) {}
        val holder = adapter.onCreateViewHolder(FrameLayout(activity), 0)

        adapter.onBindViewHolder(holder, 0)

        val builtinLabel = activity.getString(R.string.storage_builtin_label)
        val visibilityLabel = activity.getString(R.string.storage_hide)
        val infoText = holder.info.text.toString()
        assertTrue(infoText.contains(builtinLabel))
        assertTrue(infoText.contains(visibilityLabel))
        assertEquals(activity.getString(R.string.storage_unhide), holder.action.text.toString())
    }
}
