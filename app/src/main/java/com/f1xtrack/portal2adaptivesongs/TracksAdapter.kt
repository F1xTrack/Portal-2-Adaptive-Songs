package com.f1xtrack.portal2adaptivesongs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class TracksAdapter(
    private var tracks: List<TrackInfo>,
    private var selected: String?,
    private val onTrackClick: (TrackInfo) -> Unit
) : RecyclerView.Adapter<TracksAdapter.TrackViewHolder>() {

    data class TrackInfo(
        val name: String,
        val duration: Int, // ms
        val plays: Int
    )

    fun updateData(newTracks: List<TrackInfo>, selectedTrack: String?) {
        tracks = newTracks
        selected = selectedTrack
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_track_card, parent, false)
        return TrackViewHolder(view)
    }

    override fun getItemCount(): Int = tracks.size

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        val track = tracks[position]
        holder.bind(track, track.name == selected)
        holder.itemView.setOnClickListener { onTrackClick(track) }
    }

    class TrackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card = itemView as MaterialCardView
        private val name: TextView = itemView.findViewById(R.id.textTrackName)
        private val duration: TextView = itemView.findViewById(R.id.textTrackDuration)
        private val plays: TextView = itemView.findViewById(R.id.textTrackPlays)
        private val icon: ImageView = itemView.findViewById(R.id.iconTrack)

        fun bind(track: TrackInfo, isSelected: Boolean) {
            name.text = track.name
            duration.text = formatDuration(track.duration)
            plays.text = itemView.context.resources.getQuantityString(
                R.plurals.plays_count, track.plays, track.plays
            )
            card.strokeWidth = if (isSelected) 6 else 0
            card.strokeColor = if (isSelected) itemView.context.getColor(R.color.portal_orange) else 0
            icon.alpha = if (isSelected) 1f else 0.7f
        }

        private fun formatDuration(ms: Int): String {
            val totalSec = ms / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return String.format("%d:%02d", min, sec)
        }
    }
} 