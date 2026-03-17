package com.challengo.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.challengo.app.R
import com.challengo.app.data.model.Comment
import com.squareup.picasso.Picasso

class CommentAdapter : ListAdapter<Comment, CommentAdapter.CommentViewHolder>(CommentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivProfileImage: ImageView = itemView.findViewById(R.id.ivProfileImage)
        private val tvUsername: TextView = itemView.findViewById(R.id.tvUsername)
        private val tvCommentText: TextView = itemView.findViewById(R.id.tvCommentText)
        private val tvTimeAgo: TextView = itemView.findViewById(R.id.tvTimeAgo)

        fun bind(comment: Comment) {
            val username = comment.username.trim().ifEmpty {
                itemView.context.getString(R.string.unknown_user)
            }
            tvUsername.text = username
            tvCommentText.text = comment.text
            tvTimeAgo.text = comment.getTimeAgo()

            if (comment.userProfileImageUri.isNullOrBlank()) {
                ivProfileImage.setImageResource(R.drawable.challengo_avatar)
            } else {
                Picasso.get().load(comment.userProfileImageUri).into(ivProfileImage)
            }
        }
    }

    class CommentDiffCallback : DiffUtil.ItemCallback<Comment>() {
        override fun areItemsTheSame(oldItem: Comment, newItem: Comment): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Comment, newItem: Comment): Boolean {
            return oldItem == newItem
        }
    }
}
