package com.f1xtrack.portal2adaptivesongs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

/**
 * Адаптер для отображения списка треков в RecyclerView
 * 
 * Основные функции:
 * - Отображение информации о треках (название, длительность, количество воспроизведений)
 * - Выделение выбранного трека визуально
 * - Обработка кликов по трекам
 * - Поддержка обновления данных и выбранного элемента
 */
class TracksAdapter(
    private var tracks: List<TrackInfo>,           // Список треков для отображения
    private var selected: String?,                 // Название выбранного трека
    private val onTrackClick: (TrackInfo) -> Unit  // Колбэк при клике на трек
) : RecyclerView.Adapter<TracksAdapter.TrackViewHolder>() {

    /**
     * Класс данных для хранения информации о треке
     * 
     * @param name Название трека
     * @param duration Длительность в миллисекундах
     * @param plays Количество воспроизведений
     */
    data class TrackInfo(
        val name: String,
        val duration: Int, // ms
        val plays: Int
    )

    /**
     * Обновление данных адаптера
     * Вызывается при изменении списка треков или выбранного элемента
     * 
     * @param newTracks Новый список треков
     * @param selectedTrack Название выбранного трека
     */
    fun updateData(newTracks: List<TrackInfo>, selectedTrack: String?) {
        tracks = newTracks
        selected = selectedTrack
        notifyDataSetChanged() // Уведомляем RecyclerView об изменении данных
    }

    /**
     * Создание нового ViewHolder для элемента списка
     * 
     * @param parent Родительская ViewGroup
     * @param viewType Тип представления (не используется в данном адаптере)
     * @return Новый TrackViewHolder
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_track_card, parent, false)
        return TrackViewHolder(view)
    }

    /**
     * Возвращает количество элементов в списке
     * 
     * @return Размер списка треков
     */
    override fun getItemCount(): Int = tracks.size

    /**
     * Привязка данных к ViewHolder
     * Вызывается для каждого видимого элемента списка
     * 
     * @param holder ViewHolder для привязки данных
     * @param position Позиция элемента в списке
     */
    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        val track = tracks[position]
        val isSelected = track.name == selected
        
        // Привязываем данные к ViewHolder
        holder.bind(track, isSelected)
        
        // Устанавливаем обработчик клика
        holder.itemView.setOnClickListener { onTrackClick(track) }
    }

    /**
     * ViewHolder для элемента списка треков
     * Содержит ссылки на все View элементы карточки трека
     */
    class TrackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        
        // Ссылки на элементы интерфейса
        private val card = itemView as MaterialCardView      // Основная карточка
        private val name: TextView = itemView.findViewById(R.id.textTrackName)        // Название трека
        private val duration: TextView = itemView.findViewById(R.id.textTrackDuration) // Длительность
        private val plays: TextView = itemView.findViewById(R.id.textTrackPlays)      // Количество воспроизведений
        private val icon: ImageView = itemView.findViewById(R.id.iconTrack)           // Иконка трека

        /**
         * Привязка данных трека к элементам интерфейса
         * 
         * @param track Информация о треке
         * @param isSelected Флаг выбранного трека
         */
        fun bind(track: TrackInfo, isSelected: Boolean) {
            // Устанавливаем текст
            name.text = track.name
            duration.text = formatDuration(track.duration)
            plays.text = "${track.plays} прослушиваний"
            
            // Визуальное выделение выбранного трека
            if (isSelected) {
                card.strokeWidth = 6  // Толстая рамка
                card.strokeColor = itemView.context.getColor(R.color.portal_orange) // Оранжевый цвет
                icon.alpha = 1f       // Полная непрозрачность иконки
            } else {
                card.strokeWidth = 0  // Без рамки
                card.strokeColor = 0  // Прозрачный цвет рамки
                icon.alpha = 0.7f     // Полупрозрачная иконка
            }
        }

        /**
         * Форматирование длительности из миллисекунд в формат MM:SS
         * 
         * @param ms Длительность в миллисекундах
         * @return Отформатированная строка длительности
         */
        private fun formatDuration(ms: Int): String {
            val totalSec = ms / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return String.format("%d:%02d", min, sec)
        }
    }
} 