package com.f1xtrack.portal2adaptivesongs

import android.widget.FrameLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TracksAdapterTest {

    @Test
    fun `binds selected track details and click callback`() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val track = TracksAdapter.TrackInfo(name = "Portal Radio", duration = 65_000, plays = 3)
        var clicked: TracksAdapter.TrackInfo? = null

        val adapter = TracksAdapter(listOf(track), "Portal Radio") { clicked = it }
        val parent = FrameLayout(activity)
        val holder = adapter.onCreateViewHolder(parent, 0)

        adapter.onBindViewHolder(holder, 0)
        holder.itemView.performClick()

        assertEquals("Portal Radio", holder.itemView.findViewById<TextView>(R.id.textTrackName).text.toString())
        assertEquals("1:05", holder.itemView.findViewById<TextView>(R.id.textTrackDuration).text.toString())
        assertTrue(holder.itemView.findViewById<TextView>(R.id.textTrackPlays).text.toString().contains("3"))
        assertEquals(6, (holder.itemView as MaterialCardView).strokeWidth)
        assertEquals(track, clicked)
    }
}
