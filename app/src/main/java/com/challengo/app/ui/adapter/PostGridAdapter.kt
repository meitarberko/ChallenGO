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
import com.squareup.picasso.Callback
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

    override fun onViewRecycled(holder: PostGridViewHolder) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    inner class PostGridViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPostImage: ImageView = itemView.findViewById(R.id.ivPostImage)

        fun bind(post: Post) {
            Picasso.get().cancelRequest(ivPostImage)
            if (post.postImageUri.isNullOrBlank()) {
                ivPostImage.setImageDrawable(null)
            } else {
                Picasso.get()
                    .load(Uri.parse(post.postImageUri))
                    .noPlaceholder()
                    .error(R.drawable.post_image_placeholder)
                    .into(ivPostImage, object : Callback {
                        override fun onSuccess() = Unit

                        override fun onError(e: Exception?) {
                            ivPostImage.setImageResource(R.drawable.post_image_placeholder)
                        }
                    })
            }
            itemView.setOnClickListener { onPostClick(post) }
        }

        fun recycle() {
            Picasso.get().cancelRequest(ivPostImage)
            ivPostImage.setImageDrawable(null)
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
