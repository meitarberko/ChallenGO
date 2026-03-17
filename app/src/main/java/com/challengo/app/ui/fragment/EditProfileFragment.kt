package com.challengo.app.ui.fragment

import android.animation.ObjectAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.challengo.app.R
import com.challengo.app.databinding.FragmentEditProfileBinding
import com.challengo.app.data.repository.UserRepository
import com.challengo.app.di.AppModule
import com.challengo.app.ui.viewmodel.ProfileViewModel
import com.challengo.app.ui.viewmodel.ViewModelFactory
import com.squareup.picasso.Picasso
import kotlinx.coroutines.launch

class EditProfileFragment : Fragment() {
    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!
    
    private var selectedImageUri: Uri? = null
    private var removeProfileImageRequested = false
    private var originalUsername: String? = null
    private var originalProfileImageUri: String? = null
    private var userEditedUsername = false
    private var isSettingUsernameText = false
    private var isSaving = false
    private var sparkleAnimator: ObjectAnimator? = null
    private val currentUserId: String? by lazy {
        AppModule.provideFirebaseAuth().currentUser?.uid
    }
    
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            persistReadPermission(uri)
            selectedImageUri = uri
            removeProfileImageRequested = false
            Picasso.get().load(uri).into(binding.ivProfileImage)
            updateSaveButtonEnabledState()
        }
    }
    
    private val viewModel: ProfileViewModel by viewModels {
        val firestore = AppModule.provideFirestore()
        val db = AppModule.provideDatabase(requireContext().applicationContext as android.app.Application)
        val userRepo = AppModule.provideUserRepository(
            firestore,
            db.userDao()
        )
        val postRepo = AppModule.providePostRepository(
            firestore,
            db.postDao()
        )
        val likeRepo = AppModule.provideLikeRepository(
            firestore,
            db.postLikeDao(),
            AppModule.provideNotificationRepository(firestore),
            postRepo
        )
        ViewModelFactory {
            ProfileViewModel(userRepo, postRepo, likeRepo, currentUserId ?: "", currentUserId ?: "")
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditProfileBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyInsets()
        startHeaderAccentAnimation()
        updateSaveButtonEnabledState()
        
        binding.btnSelectImage.setOnClickListener {
            imagePickerLauncher.launch(arrayOf("image/*"))
        }
        binding.btnDeleteImage.setOnClickListener {
            selectedImageUri = null
            removeProfileImageRequested = true
            binding.ivProfileImage.setImageResource(R.drawable.challengo_avatar)
            updateSaveButtonEnabledState()
        }
        
        binding.btnSave.setOnClickListener {
            val usernameInput = binding.etUsername.text.toString().trim()
            val currentUsername = originalUsername.orEmpty().trim()
            val usernameToUpdate = usernameInput.takeIf { it.isNotBlank() && it != currentUsername }
            if (usernameToUpdate != null && !UserRepository.isUsernameFormatValid(usernameToUpdate)) {
                binding.tilUsername.error = getString(R.string.error_username_required)
                binding.etUsername.requestFocus()
                return@setOnClickListener
            }
            val imageToUpdate = selectedImageUri?.takeIf { it.toString() != originalProfileImageUri }
            val clearProfileImage = removeProfileImageRequested && !originalProfileImageUri.isNullOrBlank()
            if (usernameToUpdate == null && imageToUpdate == null && !clearProfileImage) {
                return@setOnClickListener
            }
            binding.tilUsername.error = null
            viewModel.updateProfile(usernameToUpdate, imageToUpdate, clearProfileImage)
        }
        binding.btnCancel.setOnClickListener { findNavController().popBackStack() }

        binding.etUsername.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (!isSettingUsernameText) {
                    userEditedUsername = true
                }
                if (!binding.tilUsername.error.isNullOrEmpty()) {
                    binding.tilUsername.error = null
                }
                updateSaveButtonEnabledState()
            }
        })
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.user.collect { user ->
                    user?.let {
                        if (originalUsername == null || (originalUsername.orEmpty().isBlank() && !it.username.isNullOrBlank())) {
                            originalUsername = it.username
                        }
                        if (originalProfileImageUri == null) {
                            originalProfileImageUri = it.profileImageUri
                        }
                        if (!userEditedUsername && binding.etUsername.text?.toString()?.trim().isNullOrEmpty() && it.username.isNotBlank()) {
                            isSettingUsernameText = true
                            binding.etUsername.setText(it.username)
                            binding.etUsername.setSelection(binding.etUsername.text?.length ?: 0)
                            isSettingUsernameText = false
                        }
                        if (removeProfileImageRequested) {
                            binding.ivProfileImage.setImageResource(R.drawable.challengo_avatar)
                        } else if (selectedImageUri == null && it.profileImageUri.isNullOrBlank()) {
                            binding.ivProfileImage.setImageResource(R.drawable.challengo_avatar)
                        } else if (selectedImageUri == null) {
                            Picasso.get()
                                .load(Uri.parse(it.profileImageUri))
                                .placeholder(R.drawable.challengo_avatar)
                                .error(R.drawable.challengo_avatar)
                                .into(binding.ivProfileImage)
                        }
                        updateSaveButtonEnabledState()
                    }
                }
            }
        }
        
        viewModel.updateState.observe(viewLifecycleOwner, Observer { state ->
            when (state) {
                is com.challengo.app.ui.viewmodel.ProfileUpdateState.Loading -> {
                    isSaving = true
                    binding.progressBar.visibility = View.VISIBLE
                    updateSaveButtonEnabledState()
                }
                is com.challengo.app.ui.viewmodel.ProfileUpdateState.Success -> {
                    isSaving = false
                    binding.progressBar.visibility = View.GONE
                    updateSaveButtonEnabledState()
                    Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }
                is com.challengo.app.ui.viewmodel.ProfileUpdateState.Error -> {
                    isSaving = false
                    binding.progressBar.visibility = View.GONE
                    updateSaveButtonEnabledState()
                    when (state.message) {
                        UserRepository.ERROR_USERNAME_TAKEN -> {
                            binding.tilUsername.error = getString(R.string.error_username_taken)
                            binding.etUsername.requestFocus()
                        }
                        UserRepository.ERROR_USERNAME_INVALID -> {
                            binding.tilUsername.error = getString(R.string.error_username_required)
                            binding.etUsername.requestFocus()
                        }
                        else -> {
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }

    private fun applyInsets() {
        val baseStart = binding.editProfileScroll.paddingStart
        val baseTop = binding.editProfileScroll.paddingTop
        val baseEnd = binding.editProfileScroll.paddingEnd
        val baseBottom = binding.editProfileScroll.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.editProfileScroll) { view, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = baseStart,
                top = baseTop + systemBarsInsets.top,
                right = baseEnd,
                bottom = baseBottom + systemBarsInsets.bottom
            )
            insets
        }
    }

    private fun startHeaderAccentAnimation() {
        sparkleAnimator?.cancel()
        sparkleAnimator = ObjectAnimator.ofFloat(binding.sparkleTop, View.ALPHA, 0.25f, 0.55f).apply {
            duration = 2400L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun setSaveButtonEnabled(enabled: Boolean) {
        binding.btnSave.isEnabled = enabled
        binding.btnSave.alpha = if (enabled) 1f else 0.45f
    }

    private fun hasPendingChanges(): Boolean {
        val currentUsername = binding.etUsername.text?.toString()?.trim().orEmpty()
        val usernameChanged = currentUsername.isNotBlank() && currentUsername != originalUsername.orEmpty().trim()
        val imageChanged = selectedImageUri?.toString()?.let { it != originalProfileImageUri } == true
        val imageDeleted = removeProfileImageRequested && !originalProfileImageUri.isNullOrBlank()
        return usernameChanged || imageChanged || imageDeleted
    }

    private fun updateSaveButtonEnabledState() {
        setSaveButtonEnabled(!isSaving && hasPendingChanges())
    }

    private fun persistReadPermission(uri: Uri) {
        val resolver = requireContext().contentResolver
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
    }
    
    override fun onDestroyView() {
        sparkleAnimator?.cancel()
        sparkleAnimator = null
        super.onDestroyView()
        _binding = null
    }
}
