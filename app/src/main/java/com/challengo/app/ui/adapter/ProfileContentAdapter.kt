package com.challengo.app.ui.adapter

import android.net.Uri
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.challengo.app.R
import com.challengo.app.data.model.Post
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso

class ProfileContentAdapter(
    private val isOwnProfile: Boolean,
    private val onEditProfileClick: () -> Unit,
    private val onLogoutClick: () -> Unit,
    private val onPostClick: (Post) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_POST = 1
        const val VIEW_TYPE_EMPTY = 2
    }

    private var headerState = ProfileHeaderState()
    private var posts: List<Post> = emptyList()

    override fun getItemCount(): Int {
        return if (posts.isEmpty()) 2 else 1 + posts.size
    }

    override fun getItemViewType(position: Int): Int {
        if (position == 0) return VIEW_TYPE_HEADER
        return if (posts.isEmpty()) VIEW_TYPE_EMPTY else VIEW_TYPE_POST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(
                inflater.inflate(R.layout.item_profile_header, parent, false)
            )

            VIEW_TYPE_POST -> PostViewHolder(
                inflater.inflate(R.layout.item_profile_post_grid, parent, false)
            )

            else -> EmptyViewHolder(
                inflater.inflate(R.layout.item_profile_posts_empty, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> holder.bind(headerState)
            is PostViewHolder -> holder.bind(posts[position - 1])
            is EmptyViewHolder -> Unit
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        when (holder) {
            is HeaderViewHolder -> holder.recycle()
            is PostViewHolder -> holder.recycle()
        }
        super.onViewRecycled(holder)
    }

    fun submitHeader(state: ProfileHeaderState) {
        headerState = state
        notifyItemChanged(0)
    }

    fun submitPosts(list: List<Post>) {
        posts = list
        notifyDataSetChanged()
    }

    private inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivProfileImage: ImageView = itemView.findViewById(R.id.ivProfileImage)
        private val tvUsername: TextView = itemView.findViewById(R.id.tvUsername)
        private val tvLevel: TextView = itemView.findViewById(R.id.tvLevel)
        private val tvPoints: TextView = itemView.findViewById(R.id.tvPoints)
        private val tvChallenges: TextView = itemView.findViewById(R.id.tvChallenges)
        private val chipGroupHashtags: ChipGroup = itemView.findViewById(R.id.chipGroupHashtags)
        private val tvHashtagsEmpty: TextView = itemView.findViewById(R.id.tvHashtagsEmpty)
        private val ivEditAvatar: ImageView = itemView.findViewById(R.id.ivEditAvatar)
        private val btnLogout: MaterialButton = itemView.findViewById(R.id.btnLogout)

        fun bind(state: ProfileHeaderState) {
            Picasso.get().cancelRequest(ivProfileImage)
            tvUsername.text = state.username
            tvLevel.text = "Adventure Seeker \u2022 Lv ${state.level}"
            tvPoints.text = state.points.toString()
            tvChallenges.text = state.challenges.toString()

            if (state.profileImageUri.isNullOrBlank()) {
                ivProfileImage.setImageResource(R.drawable.challengo_avatar)
            } else {
                Picasso.get()
                    .load(Uri.parse(state.profileImageUri))
                    .placeholder(R.drawable.challengo_avatar)
                    .error(R.drawable.challengo_avatar)
                    .into(ivProfileImage)
            }

            chipGroupHashtags.removeAllViews()
            if (state.hashtags.isEmpty()) {
                tvHashtagsEmpty.visibility = View.VISIBLE
            } else {
                tvHashtagsEmpty.visibility = View.GONE
                state.hashtags.forEach { tag ->
                    val chip = Chip(itemView.context).apply {
                        text = "#${tag.removePrefix("#")}"
                        isCheckable = false
                        isClickable = false
                        setTextColor(0xFFFFFFFF.toInt())
                        chipBackgroundColor = ColorStateList.valueOf(0xFF253F57.toInt())
                        chipStrokeColor = ColorStateList.valueOf(0xFF62EFFF.toInt())
                        chipStrokeWidth = 1f
                    }
                    chipGroupHashtags.addView(chip)
                }
            }

            ivEditAvatar.visibility = if (isOwnProfile) View.VISIBLE else View.GONE
            btnLogout.visibility = if (isOwnProfile) View.VISIBLE else View.GONE
            ivEditAvatar.setOnClickListener { onEditProfileClick() }
            btnLogout.setOnClickListener { onLogoutClick() }
        }

        fun recycle() {
            Picasso.get().cancelRequest(ivProfileImage)
            ivProfileImage.setImageDrawable(null)
        }
    }

    private inner class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPostImage: ImageView = itemView.findViewById(R.id.ivGridPostImage)

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

    private class EmptyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}

data class ProfileHeaderState(
    val username: String = "",
    val level: Int = 1,
    val points: Int = 0,
    val challenges: Int = 0,
    val profileImageUri: String? = null,
    val hashtags: List<String> = emptyList()
)
