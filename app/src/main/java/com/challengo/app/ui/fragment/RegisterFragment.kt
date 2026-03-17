package com.challengo.app.ui.fragment

import android.net.Uri
import android.os.Bundle
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.challengo.app.R
import com.challengo.app.data.repository.AuthRepository
import com.challengo.app.data.repository.UserRepository
import com.challengo.app.databinding.FragmentRegisterBinding
import com.challengo.app.di.AppModule
import com.challengo.app.notifications.NotificationScheduler
import com.challengo.app.ui.animation.GlossSweepAnimator
import com.challengo.app.ui.common.attachPasswordToggle
import com.challengo.app.ui.viewmodel.AuthViewModel
import com.challengo.app.ui.viewmodel.RegisterUiState
import com.challengo.app.ui.viewmodel.ViewModelFactory
import com.google.android.material.textfield.TextInputLayout
import com.squareup.picasso.Picasso
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {
    private var _binding: FragmentRegisterBinding? = null
    private val binding: FragmentRegisterBinding
        get() = requireNotNull(_binding)
    private val nameRegex = Regex("^[\\p{L}]+$")
    private var registerGlossAnimator: GlossSweepAnimator? = null

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            persistReadPermission(uri)
            viewModel.onProfileImageSelected(uri)
        }
    }

    private val viewModel: AuthViewModel by viewModels {
        val authRepo = AppModule.provideAuthRepository(
            AppModule.provideFirebaseAuth(),
            AppModule.provideFirestore(),
            AppModule.provideDatabase(requireContext().applicationContext as android.app.Application).userDao()
        )
        ViewModelFactory { AuthViewModel(authRepo) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        registerGlossAnimator = GlossSweepAnimator(binding.btnRegister, binding.registerGlossView).also { it.bind() }
        binding.tilPassword.attachPasswordToggle(binding.etPassword)
        setupRealtimeValidation()
        setupKeyboardNavigation()
        setupImagePicker()

        binding.tvLogin.setOnClickListener {
            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
        }

        binding.btnRegister.setOnClickListener {
            submitRegisterIfValid()
        }

        collectSelectedProfileImage()
        collectRegisterState()
    }

    private fun setupImagePicker() {
        binding.ivRegisterAvatar.setOnClickListener { openImagePicker() }
        binding.btnPickPhoto.setOnClickListener { openImagePicker() }
        renderSelectedProfileImage(null)
    }

    private fun openImagePicker() {
        imagePickerLauncher.launch(arrayOf("image/*"))
    }

    private fun persistReadPermission(uri: Uri) {
        val resolver = requireContext().contentResolver
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
    }

    private fun collectSelectedProfileImage() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedProfileImageUri.collect { uri ->
                    renderSelectedProfileImage(uri)
                }
            }
        }
    }

    private fun renderSelectedProfileImage(uri: Uri?) {
        if (uri == null) {
            binding.ivRegisterAvatar.setImageResource(R.drawable.challengo_avatar)
            binding.btnPickPhoto.text = getString(R.string.register_add_photo)
        } else {
            Picasso.get().load(uri).fit().centerCrop().into(binding.ivRegisterAvatar)
            binding.btnPickPhoto.text = getString(R.string.register_change_photo)
        }
    }

    private fun setupKeyboardNavigation() {
        binding.etFirstName.setOnEditorActionListener(moveFocusAction(binding.etLastName))
        binding.etLastName.setOnEditorActionListener(moveFocusAction(binding.etUsername))
        binding.etUsername.setOnEditorActionListener(moveFocusAction(binding.etEmail))
        binding.etEmail.setOnEditorActionListener(moveFocusAction(binding.etPassword))
        binding.etPassword.setOnEditorActionListener(moveFocusAction(binding.etAge))
        binding.etAge.setOnEditorActionListener { _, actionId, event ->
            if (isActionTriggered(actionId, event)) {
                submitRegisterIfValid()
                true
            } else {
                false
            }
        }
    }

    private fun moveFocusAction(nextField: View): TextView.OnEditorActionListener {
        return TextView.OnEditorActionListener { _, actionId, event ->
            if (isActionTriggered(actionId, event)) {
                nextField.requestFocus()
                true
            } else {
                false
            }
        }
    }

    private fun isActionTriggered(actionId: Int, event: KeyEvent?): Boolean {
        return actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT ||
            actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
            (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
    }

    private fun setupRealtimeValidation() {
        addValidationWatcher(binding.etFirstName) { validateFirstName(false) }
        addValidationWatcher(binding.etLastName) { validateLastName(false) }
        addValidationWatcher(binding.etUsername) { validateUsername(false) }
        addValidationWatcher(binding.etEmail) { validateEmail(false) }
        addValidationWatcher(binding.etPassword) { validatePassword(false) }
        addValidationWatcher(binding.etAge) { validateAge(false) }
    }

    private fun addValidationWatcher(editText: android.widget.EditText, validation: () -> Unit) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                validation()
            }
        })
    }

    private fun submitRegisterIfValid() {
        val firstValid = validateFirstName(true)
        val lastValid = validateLastName(true)
        val usernameValid = validateUsername(true)
        val emailValid = validateEmail(true)
        val passwordValid = validatePassword(true)
        val ageValid = validateAge(true)

        if (!(firstValid && lastValid && usernameValid && emailValid && passwordValid && ageValid)) {
            return
        }

        val firstName = binding.etFirstName.text.toString().trim()
        val lastName = binding.etLastName.text.toString().trim()
        val username = binding.etUsername.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val age = binding.etAge.text.toString().trim().toInt()

        viewModel.register(email, password, username, firstName, lastName, age)
    }

    private fun validateFirstName(force: Boolean): Boolean {
        val value = binding.etFirstName.text.toString().trim()
        if (!force && value.isEmpty()) {
            setFieldState(binding.tilFirstName, binding.etFirstName, null)
            return false
        }
        val isValid = nameRegex.matches(value)
        setFieldState(
            binding.tilFirstName,
            binding.etFirstName,
            if (isValid) null else getString(R.string.error_first_name_letters_only)
        )
        return isValid
    }

    private fun validateLastName(force: Boolean): Boolean {
        val value = binding.etLastName.text.toString().trim()
        if (!force && value.isEmpty()) {
            setFieldState(binding.tilLastName, binding.etLastName, null)
            return false
        }
        val isValid = nameRegex.matches(value)
        setFieldState(
            binding.tilLastName,
            binding.etLastName,
            if (isValid) null else getString(R.string.error_last_name_letters_only)
        )
        return isValid
    }

    private fun validateUsername(force: Boolean): Boolean {
        val value = binding.etUsername.text.toString().trim()
        if (!force && value.isEmpty()) {
            setFieldState(binding.tilUsername, binding.etUsername, null)
            return false
        }
        val isValid = UserRepository.isUsernameFormatValid(value)
        setFieldState(binding.tilUsername, binding.etUsername, if (isValid) null else getString(R.string.error_username_required))
        return isValid
    }

    private fun validateEmail(force: Boolean): Boolean {
        val value = binding.etEmail.text.toString().trim()
        if (!force && value.isEmpty()) {
            setFieldState(binding.tilEmail, binding.etEmail, null)
            return false
        }
        val isValid = Patterns.EMAIL_ADDRESS.matcher(value).matches()
        setFieldState(binding.tilEmail, binding.etEmail, if (isValid) null else getString(R.string.error_invalid_email))
        return isValid
    }

    private fun validatePassword(force: Boolean): Boolean {
        val value = binding.etPassword.text.toString().trim()
        if (!force && value.isEmpty()) {
            setFieldState(binding.tilPassword, binding.etPassword, null)
            return false
        }
        val isValid = value.length >= 5
        setFieldState(binding.tilPassword, binding.etPassword, if (isValid) null else getString(R.string.error_password_min_5))
        return isValid
    }

    private fun collectRegisterState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.registerUiState.collect { state ->
                    when (state) {
                        is RegisterUiState.Idle -> {
                            binding.btnRegister.isEnabled = true
                            binding.progressBar.visibility = View.GONE
                        }
                        is RegisterUiState.Loading -> {
                            binding.btnRegister.isEnabled = false
                            binding.progressBar.visibility = View.VISIBLE
                        }
                        is RegisterUiState.Success -> {
                            binding.btnRegister.isEnabled = true
                            binding.progressBar.visibility = View.GONE
                            NotificationScheduler.scheduleRecurring(requireContext().applicationContext)
                            viewModel.consumeRegisterState()
                            navigateToHomeClearingAuthStack()
                        }
                        is RegisterUiState.Error -> {
                            binding.btnRegister.isEnabled = true
                            binding.progressBar.visibility = View.GONE
                            when (state.message) {
                                AuthRepository.ERROR_EMAIL_ALREADY_REGISTERED -> {
                                    setFieldState(binding.tilEmail, binding.etEmail, getString(R.string.error_email_already_registered))
                                    binding.etEmail.requestFocus()
                                }
                                AuthRepository.ERROR_USERNAME_TAKEN -> {
                                    setFieldState(binding.tilUsername, binding.etUsername, getString(R.string.error_username_taken))
                                    binding.etUsername.requestFocus()
                                }
                                else -> {
                                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                                }
                            }
                            viewModel.consumeRegisterState()
                        }
                    }
                }
            }
        }
    }

    private fun navigateToHomeClearingAuthStack() {
        val navOptions = NavOptions.Builder()
            .setPopUpTo(R.id.nav_graph, true)
            .setLaunchSingleTop(true)
            .build()
        findNavController().navigate(R.id.homeFragment, null, navOptions)
    }

    private fun validateAge(force: Boolean): Boolean {
        val value = binding.etAge.text.toString().trim()
        if (!force && value.isEmpty()) {
            setFieldState(binding.tilAge, binding.etAge, null)
            return false
        }
        val age = value.toIntOrNull()
        val isValid = age != null && age >= 14
        setFieldState(binding.tilAge, binding.etAge, if (isValid) null else getString(R.string.error_age_min_14))
        return isValid
    }

    private fun setFieldState(
        textInputLayout: TextInputLayout,
        editText: android.widget.EditText,
        errorMessage: String?
    ) {
        textInputLayout.error = errorMessage
        editText.setBackgroundResource(
            if (errorMessage == null) R.drawable.register_input_bg else R.drawable.register_input_bg_error
        )
    }

    override fun onDestroyView() {
        registerGlossAnimator?.clear()
        registerGlossAnimator = null
        super.onDestroyView()
        _binding = null
    }
}
