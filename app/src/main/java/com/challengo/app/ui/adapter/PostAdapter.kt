package com.challengo.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Callback
import com.challengo.app.R
import com.challengo.app.data.model.Post
import com.squareup.picasso.Picasso
import android.net.Uri

class PostAdapter(
    private val currentUserId: String?,
    private val onPostClick: (Post) -> Unit,
    private val onProfileClick: (String) -> Unit,
    private val onLikeClick: (Post) -> Unit,
    private val onCommentClick: (Post) -> Unit
) : ListAdapter<Post, PostAdapter.PostViewHolder>(PostDiffCallback()) {
    private val likedPostIds = mutableSetOf<String>()

    fun submitLikedState(likedState: Map<String, Boolean>) {
        likedPostIds.clear()
        likedPostIds.addAll(likedState.filterValues { it }.keys)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: PostViewHolder) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    inner class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivProfileImage: ImageView = itemView.findViewById(R.id.ivProfileImage)
        private val tvUsername: TextView = itemView.findViewById(R.id.tvUsername)
        private val tvTimeAgo: TextView = itemView.findViewById(R.id.tvTimeAgo)
        private val ivPostImage: ImageView = itemView.findViewById(R.id.ivPostImage)
        private val tvPostText: TextView = itemView.findViewById(R.id.tvPostText)
        private val tvHashtag: TextView = itemView.findViewById(R.id.tvHashtag)
        private val likeGroup: LinearLayout = itemView.findViewById(R.id.likeGroup)
        private val btnLike: ImageButton = itemView.findViewById(R.id.btnLike)
        private val tvLikesCount: TextView = itemView.findViewById(R.id.tvLikesCount)
        private val commentGroup: LinearLayout = itemView.findViewById(R.id.commentGroup)
        private val btnComment: ImageButton = itemView.findViewById(R.id.btnComment)
        private val tvCommentsCount: TextView = itemView.findViewById(R.id.tvCommentsCount)

        fun bind(post: Post) {
            tvUsername.text = post.username
            tvTimeAgo.text = post.getTimeAgo()
            tvPostText.text = post.text
            tvHashtag.text = "#${post.hashtag}"
            tvLikesCount.text = post.likesCount.toString()
            tvCommentsCount.text = post.commentsCount.toString()

            Picasso.get().cancelRequest(ivProfileImage)
            Picasso.get().cancelRequest(ivPostImage)

            if (post.userProfileImageUri.isNullOrBlank()) {
                ivProfileImage.setImageResource(R.drawable.challengo_avatar)
            } else {
                Picasso.get()
                    .load(Uri.parse(post.userProfileImageUri))
                    .placeholder(R.drawable.challengo_avatar)
                    .error(R.drawable.challengo_avatar)
                    .into(ivProfileImage)
            }

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

            val isLiked = likedPostIds.contains(post.id)
            applyLikeVisual(isLiked)

            itemView.setOnClickListener { onPostClick(post) }
            ivProfileImage.setOnClickListener { onProfileClick(post.userId) }
            tvUsername.setOnClickListener { onProfileClick(post.userId) }
            likeGroup.setOnClickListener { onLikeClick(post) }
            btnLike.setOnClickListener { likeGroup.performClick() }
            commentGroup.setOnClickListener { onCommentClick(post) }
            btnComment.setOnClickListener { commentGroup.performClick() }
        }

        fun recycle() {
            Picasso.get().cancelRequest(ivProfileImage)
            Picasso.get().cancelRequest(ivPostImage)
            ivProfileImage.setImageDrawable(null)
            ivPostImage.setImageDrawable(null)
        }

        private fun applyLikeVisual(isLiked: Boolean) {
            if (isLiked) {
                btnLike.setImageResource(R.drawable.ic_heart_filled)
                btnLike.imageTintList = null
            } else {
                btnLike.setImageResource(R.drawable.ic_heart_outline)
                btnLike.imageTintList = null
            }
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
