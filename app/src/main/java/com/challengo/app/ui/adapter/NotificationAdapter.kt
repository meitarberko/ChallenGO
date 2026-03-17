package com.challengo.app.ui.adapter

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.challengo.app.R
import com.challengo.app.data.model.AppNotification
import com.challengo.app.data.repository.NotificationRepository
import java.util.concurrent.TimeUnit

class NotificationAdapter(
    private val onClick: (AppNotification) -> Unit
) : ListAdapter<AppNotification, NotificationAdapter.NotificationViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivTypeIcon: ImageView = itemView.findViewById(R.id.ivTypeIcon)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvBody: TextView = itemView.findViewById(R.id.tvBody)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val unreadDot: View = itemView.findViewById(R.id.unreadDot)

        fun bind(item: AppNotification) {
            tvTitle.text = item.messageTitle
            tvBody.text = item.messageBody
            tvTime.text = relativeTime(item.relativeTimeMillis())
            unreadDot.visibility = if (item.read) View.GONE else View.VISIBLE
            tvTitle.setTypeface(null, if (item.read) Typeface.NORMAL else Typeface.BOLD)
            ivTypeIcon.setImageResource(iconForType(item.type))
            itemView.setOnClickListener { onClick(item) }
        }

        private fun iconForType(type: String): Int {
            return when (type) {
                NotificationRepository.TYPE_COMMENT -> android.R.drawable.ic_menu_edit
                NotificationRepository.TYPE_LIKE -> android.R.drawable.btn_star_big_on
                NotificationRepository.TYPE_REMINDER_CHALLENGE_NOT_DONE -> android.R.drawable.ic_lock_idle_alarm
                NotificationRepository.TYPE_REMINDER_NO_ROLL_24H -> android.R.drawable.ic_popup_reminder
                else -> android.R.drawable.ic_dialog_info
            }
        }

        private fun relativeTime(diffMillis: Long): String {
            val seconds = TimeUnit.MILLISECONDS.toSeconds(diffMillis).coerceAtLeast(0)
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24
            return when {
                days > 0 -> "${days}d ago"
                hours > 0 -> "${hours}h ago"
                minutes > 0 -> "${minutes}m ago"
                else -> "Just now"
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<AppNotification>() {
        override fun areItemsTheSame(oldItem: AppNotification, newItem: AppNotification): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AppNotification, newItem: AppNotification): Boolean {
            return oldItem == newItem
        }
    }
}