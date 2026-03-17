package com.challengo.app.ui.fragment

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.challengo.app.R
import com.challengo.app.databinding.FragmentPostDetailsBinding
import com.challengo.app.di.AppModule
import com.challengo.app.ui.adapter.CommentAdapter
import com.challengo.app.ui.viewmodel.CommentViewModel
import com.challengo.app.ui.viewmodel.PostViewModel
import com.challengo.app.ui.viewmodel.ViewModelFactory
import com.squareup.picasso.Picasso
import kotlinx.coroutines.launch

class PostDetailsFragment : Fragment() {
    private var _binding: FragmentPostDetailsBinding? = null
    private val binding get() = _binding!!

    private lateinit var commentAdapter: CommentAdapter
    private var currentLikeCount: Int = 0
    private var isCurrentlyLiked: Boolean = false
    private var didSyncLikeState = false
    private val currentUserId: String? by lazy {
        AppModule.provideFirebaseAuth().currentUser?.uid
    }

    private val postId: String by lazy {
        arguments?.getString("postId") ?: ""
    }

    private val postViewModel: PostViewModel by viewModels {
        val firestore = AppModule.provideFirestore()
        val postRepo = AppModule.providePostRepository(
            firestore,
            AppModule.provideDatabase(requireContext().applicationContext as android.app.Application).postDao()
        )
        val likeRepo = AppModule.provideLikeRepository(
            firestore,
            AppModule.provideDatabase(requireContext().applicationContext as android.app.Application).postLikeDao(),
            AppModule.provideNotificationRepository(firestore),
            postRepo
        )
        ViewModelFactory {
            PostViewModel(postRepo, likeRepo, currentUserId ?: "")
        }
    }

    private val commentViewModel: CommentViewModel by viewModels {
        val firestore = AppModule.provideFirestore()
        val commentRepo = AppModule.provideCommentRepository(
            firestore,
            AppModule.provideDatabase(requireContext().applicationContext as android.app.Application).commentDao(),
            AppModule.provideNotificationRepository(firestore)
        )
        ViewModelFactory {
            CommentViewModel(commentRepo, postId, currentUserId ?: "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPostDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.postCard.tvUsername.text = getString(R.string.unknown_user)
        val initialLikeState = postViewModel.likeStates.value[postId]
        isCurrentlyLiked = initialLikeState?.isLikedByMe == true
        currentLikeCount = initialLikeState?.likesCount ?: 0
        binding.postCard.tvLikesCount.text = currentLikeCount.toString()
        applyLikeVisual(isCurrentlyLiked)
        setupRecyclerView()
        setupObservers()

        binding.btnBack.setOnClickListener {
            val popped = findNavController().popBackStack()
            if (!popped) {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        binding.postCard.btnLike.setOnClickListener {
            postViewModel.toggleLike(postId, currentLikeCount)
        }

        binding.btnEdit.setOnClickListener {
            showEditDialog()
        }

        binding.btnDelete.setOnClickListener {
            showDeleteDialog()
        }

        binding.btnAddComment.setOnClickListener {
            val text = binding.etComment.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a comment", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val userRepo = AppModule.provideUserRepository(
                AppModule.provideFirestore(),
                AppModule.provideDatabase(requireContext().applicationContext as android.app.Application).userDao()
            )

            viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val user = userRepo.getUserSync(currentUserId ?: "")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    commentViewModel.addComment(
                        username = user?.username ?: "",
                        userProfileImageUri = user?.profileImageUri,
                        text = text
                    )
                    binding.etComment.text?.clear()
                }
            }
        }
    }

    private fun showEditDialog() {
        val editText = EditText(requireContext()).apply {
            setText(binding.postCard.tvPostText.text.toString())
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Post")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val updatedText = editText.text.toString().trim()
                if (updatedText.isNotEmpty()) {
                    postViewModel.updatePost(postId, updatedText, null)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Post")
            .setMessage("Are you sure you want to delete this post?")
            .setPositiveButton("Delete") { _, _ ->
                postViewModel.deletePost(postId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupRecyclerView() {
        commentAdapter = CommentAdapter()
        binding.rvComments.layoutManager = LinearLayoutManager(requireContext())
        binding.rvComments.adapter = commentAdapter
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                postViewModel.observePost(postId).collect { post ->
                    val fallbackUsername = getString(R.string.unknown_user)
                    if (post == null) {
                        binding.postCard.tvUsername.text = fallbackUsername
                    } else {
                        binding.postCard.tvUsername.text = post.username.ifBlank { fallbackUsername }
                        binding.postCard.tvTimeAgo.text = post.getTimeAgo()
                        binding.postCard.tvPostText.text = post.text
                        binding.postCard.tvHashtag.text = "#${post.hashtag.removePrefix("#")}"
                        currentLikeCount = post.likesCount
                        binding.postCard.tvLikesCount.text = currentLikeCount.toString()
                        postViewModel.primeLikeState(postId, currentLikeCount)
                        postViewModel.likeStates.value[postId]?.let { immediateLike ->
                            isCurrentlyLiked = immediateLike.isLikedByMe
                            currentLikeCount = immediateLike.likesCount
                            binding.postCard.tvLikesCount.text = currentLikeCount.toString()
                            applyLikeVisual(isCurrentlyLiked)
                        }
                        if (!didSyncLikeState) {
                            didSyncLikeState = true
                            postViewModel.syncLikeState(postId, currentLikeCount)
                        }
                        binding.postCard.tvCommentsCount.text = post.commentsCount.toString()
                        if (post.userProfileImageUri.isNullOrBlank()) {
                            binding.postCard.ivProfileImage.setImageResource(R.drawable.challengo_avatar)
                        } else {
                            Picasso.get()
                                .load(Uri.parse(post.userProfileImageUri))
                                .placeholder(R.drawable.challengo_avatar)
                                .error(R.drawable.challengo_avatar)
                                .into(binding.postCard.ivProfileImage)
                        }
                        if (post.postImageUri.isNullOrBlank()) {
                            binding.postCard.ivPostImage.setImageResource(R.drawable.challengo_avatar)
                        } else {
                            Picasso.get()
                                .load(Uri.parse(post.postImageUri))
                                .placeholder(R.drawable.challengo_avatar)
                                .error(R.drawable.challengo_avatar)
                                .into(binding.postCard.ivPostImage)
                        }

                        val isOwnPost = post.userId == currentUserId
                        binding.btnEdit.visibility = if (isOwnPost) View.VISIBLE else View.GONE
                        binding.btnDelete.visibility = if (isOwnPost) View.VISIBLE else View.GONE
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                commentViewModel.comments.collect { comments ->
                    commentAdapter.submitList(comments)
                    binding.tvEmptyComments.visibility = if (comments.isEmpty()) View.VISIBLE else View.GONE
                    binding.postCard.tvCommentsCount.text = comments.size.toString()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                postViewModel.likeStates.collect { likeStates ->
                    likeStates[postId]?.let { likeState ->
                        isCurrentlyLiked = likeState.isLikedByMe
                        currentLikeCount = likeState.likesCount
                        binding.postCard.tvLikesCount.text = currentLikeCount.toString()
                        applyLikeVisual(isCurrentlyLiked)
                    }
                }
            }
        }

        postViewModel.likeState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is com.challengo.app.ui.viewmodel.LikeState.Success -> Unit
                is com.challengo.app.ui.viewmodel.LikeState.Error -> {
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        postViewModel.isLiked.observe(viewLifecycleOwner) { isLiked ->
            isCurrentlyLiked = isLiked
            applyLikeVisual(isCurrentlyLiked)
        }

        postViewModel.updateState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is com.challengo.app.ui.viewmodel.UpdateState.Loading -> binding.progressBar.visibility = View.VISIBLE
                is com.challengo.app.ui.viewmodel.UpdateState.Success -> {
                    binding.progressBar.visibility = View.GONE
                }
                is com.challengo.app.ui.viewmodel.UpdateState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        postViewModel.deleteState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is com.challengo.app.ui.viewmodel.DeleteState.Loading -> binding.progressBar.visibility = View.VISIBLE
                is com.challengo.app.ui.viewmodel.DeleteState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    findNavController().popBackStack()
                }
                is com.challengo.app.ui.viewmodel.DeleteState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        commentViewModel.addState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is com.challengo.app.ui.viewmodel.AddState.Loading -> binding.progressBar.visibility = View.VISIBLE
                is com.challengo.app.ui.viewmodel.AddState.Success -> {
                    binding.progressBar.visibility = View.GONE
                }
                is com.challengo.app.ui.viewmodel.AddState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun applyLikeVisual(isLiked: Boolean) {
        if (isLiked) {
            binding.postCard.btnLike.setImageResource(R.drawable.ic_heart_filled)
            binding.postCard.btnLike.imageTintList = null
        } else {
            binding.postCard.btnLike.setImageResource(R.drawable.ic_heart_outline)
            binding.postCard.btnLike.imageTintList = null
        }
    }
}

