package com.challengo.app.ui.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.challengo.app.R
import com.challengo.app.data.model.Post
import com.squareup.picasso.Picasso

class PostGridAdapter(
    private val onPostClick: (Post) -> Unit
) : ListAdapter<Post, PostGridAdapter.PostGridViewHolder>(PostDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostGridViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post_grid, parent, false)
        return PostGridViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostGridViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PostGridViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPostImage: ImageView = itemView.findViewById(R.id.ivPostImage)

        fun bind(post: Post) {
            if (post.postImageUri.isNullOrBlank()) {
                ivPostImage.setImageResource(R.drawable.challengo_avatar)
            } else {
                Picasso.get()
                    .load(Uri.parse(post.postImageUri))
                    .placeholder(R.drawable.challengo_avatar)
                    .error(R.drawable.challengo_avatar)
                    .into(ivPostImage)
            }
            itemView.setOnClickListener { onPostClick(post) }
        }
    }

    class PostDiffCallback : DiffUtil.ItemCallback<Post>() {
        override fun areItemsTheSame(oldItem: Post, newItem: Post): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Post, newItem: Post): Boolean {
            return oldItem == newItem
        }
    }
}