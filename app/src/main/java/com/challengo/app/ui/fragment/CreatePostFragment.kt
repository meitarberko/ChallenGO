package com.challengo.app.ui.fragment

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.challengo.app.R
import com.challengo.app.databinding.FragmentCreatePostBinding
import com.challengo.app.di.AppModule
import com.challengo.app.notifications.NotificationScheduler
import com.challengo.app.ui.viewmodel.CreatePostStatus
import com.challengo.app.ui.viewmodel.CreatePostUiState
import com.challengo.app.ui.viewmodel.CreatePostViewModel
import com.challengo.app.ui.viewmodel.ViewModelFactory
import com.squareup.picasso.Picasso
import kotlinx.coroutines.launch

class CreatePostFragment : Fragment() {
    private var _binding: FragmentCreatePostBinding? = null
    private val binding get() = _binding!!

    private val currentUserId: String? by lazy {
        AppModule.provideFirebaseAuth().currentUser?.uid
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            persistReadPermission(uri)
            Picasso.get().load(uri).into(binding.ivPostImage)
            binding.ivPostImage.visibility = View.VISIBLE
            createPostViewModel.onImageSelected(uri)
        }
    }

    private val createPostViewModel: CreatePostViewModel by viewModels {
        val db = AppModule.provideDatabase(requireContext().applicationContext as android.app.Application)
        val challengeRepo = AppModule.provideChallengeRepository(
            AppModule.provideFirestore(),
            db.challengeDao()
        )
        val postRepo = AppModule.providePostRepository(
            AppModule.provideFirestore(),
            db.postDao()
        )
        ViewModelFactory {
            CreatePostViewModel(challengeRepo, postRepo, currentUserId ?: "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreatePostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSelectImage.setOnClickListener {
            imagePickerLauncher.launch(arrayOf("image/*"))
        }

        binding.btnCreatePost.setOnClickListener {
            createPostViewModel.onDescriptionChanged(binding.etPostText.text.toString())
            createPostViewModel.submitPost()
        }

        observeUiState()
    }

    private fun persistReadPermission(uri: android.net.Uri) {
        val resolver = requireContext().contentResolver
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                createPostViewModel.uiState.collect { state ->
                    bindUiState(state)
                }
            }
        }
    }

    private fun bindUiState(state: CreatePostUiState) {
        if (state.hasActiveChallenge) {
            binding.tvChallengeNameValue.text = state.challengeName
            binding.challengeHashtagChip.text = state.hashtag
        } else {
            binding.tvChallengeNameValue.text = getString(R.string.no_active_daily_challenge)
            binding.challengeHashtagChip.text = getString(R.string.challenge_hashtag_empty)
        }

        binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        binding.btnCreatePost.isEnabled = state.isCreateEnabled
        binding.btnCreatePost.alpha = if (state.isCreateEnabled) 1f else 0.5f

        when (val status = state.status) {
            is CreatePostStatus.Success -> {
                NotificationScheduler.cancelChallengeNotDoneReminders(
                    context = requireContext().applicationContext,
                    uid = currentUserId.orEmpty(),
                    challengeId = status.challengeId
                )
                Toast.makeText(requireContext(), "Post created successfully", Toast.LENGTH_SHORT).show()
                createPostViewModel.consumeStatus()
                findNavController().popBackStack()
            }

            is CreatePostStatus.Error -> {
                Toast.makeText(requireContext(), status.message, Toast.LENGTH_SHORT).show()
                createPostViewModel.consumeStatus()
            }

            else -> Unit
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
