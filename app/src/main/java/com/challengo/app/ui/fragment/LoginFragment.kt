package com.challengo.app.ui.fragment

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.challengo.app.R
import com.challengo.app.data.repository.AuthRepository
import com.challengo.app.databinding.FragmentLoginBinding
import com.challengo.app.di.AppModule
import com.challengo.app.notifications.NotificationScheduler
import com.challengo.app.ui.animation.GlossSweepAnimator
import com.challengo.app.ui.common.attachPasswordToggle
import com.challengo.app.ui.viewmodel.AuthViewModel
import com.challengo.app.ui.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val sparkleAnimators = mutableListOf<Animator>()
    private var loginGlossAnimator: GlossSweepAnimator? = null
    
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
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupLoginButtonGradient()
        binding.tilPassword.attachPasswordToggle(binding.etPassword)
        loginGlossAnimator = GlossSweepAnimator(binding.btnLogin, binding.loginGlossView).also { it.bind() }

        if (viewModel.isLoggedIn()) {
            NotificationScheduler.scheduleRecurring(requireContext().applicationContext)
            navigateToHomeClearingLogin()
            return
        }
        startSparkleAnimations()

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.error_fill_email_password), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.login(email, password)
        }

        binding.tvRegister.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }

        collectLoginState()
    }

    private fun navigateToHomeClearingLogin() {
        val navOptions = NavOptions.Builder()
            .setPopUpTo(R.id.nav_graph, true)
            .setLaunchSingleTop(true)
            .build()
        findNavController().navigate(R.id.homeFragment, null, navOptions)
    }

    private fun collectLoginState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loginState.collect { state ->
                    when (state) {
                        is com.challengo.app.ui.viewmodel.AuthState.Idle -> {
                            binding.btnLogin.isEnabled = true
                            binding.progressBar.visibility = View.GONE
                            binding.btnLogin.text = "LOGIN"
                        }
                        is com.challengo.app.ui.viewmodel.AuthState.Loading -> {
                            binding.btnLogin.isEnabled = false
                            binding.progressBar.visibility = View.VISIBLE
                            binding.btnLogin.text = ""
                        }
                        is com.challengo.app.ui.viewmodel.AuthState.Success -> {
                            binding.btnLogin.isEnabled = true
                            binding.progressBar.visibility = View.GONE
                            binding.btnLogin.text = "LOGIN"
                            NotificationScheduler.scheduleRecurring(requireContext().applicationContext)
                            viewModel.consumeLoginState()
                            navigateToHomeClearingLogin()
                        }
                        is com.challengo.app.ui.viewmodel.AuthState.Error -> {
                            binding.btnLogin.isEnabled = true
                            binding.progressBar.visibility = View.GONE
                            binding.btnLogin.text = "LOGIN"
                            val message = if (state.message == AuthRepository.ERROR_INVALID_CREDENTIALS) {
                                getString(R.string.error_wrong_login)
                            } else {
                                state.message
                            }
                            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                            viewModel.consumeLoginState()
                        }
                    }
                }
            }
        }
    }

    private fun setupLoginButtonGradient() {
        binding.btnLogin.setBackgroundResource(R.drawable.login_button_bg)
    }

    private fun startSparkleAnimations() {
        stopSparkleAnimations()

        val sparkles = listOf(
            binding.sparkle1,
            binding.sparkle2,
            binding.sparkle3,
            binding.sparkle4,
            binding.sparkle5,
            binding.sparkle6,
            binding.sparkle7,
            binding.sparkle8
        )
        val driftDp = listOf(12f, -16f, 18f, -14f, 20f, -12f, 15f, -18f)
        val durationsMs = listOf(2800L, 3300L, 4100L, 4700L, 5200L, 3900L, 5600L, 6100L)
        val delaysMs = listOf(0L, 250L, 500L, 800L, 1100L, 1500L, 1850L, 2200L)

        sparkles.forEachIndexed { index, star ->
            val driftPx = dpToPx(driftDp[index])
            val translationAnimator = if (index % 2 == 0) {
                ObjectAnimator.ofFloat(star, View.TRANSLATION_Y, 0f, driftPx)
            } else {
                ObjectAnimator.ofFloat(star, View.TRANSLATION_X, 0f, driftPx)
            }.apply {
                duration = durationsMs[index]
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
            }

            val alphaAnimator = ObjectAnimator.ofFloat(star, View.ALPHA, 0.5f, 1f).apply {
                duration = durationsMs[index] + 300L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
            }

            val scaleXAnimator = ObjectAnimator.ofFloat(star, View.SCALE_X, 0.95f, 1.05f).apply {
                duration = durationsMs[index] + 500L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
            }

            val scaleYAnimator = ObjectAnimator.ofFloat(star, View.SCALE_Y, 0.95f, 1.05f).apply {
                duration = durationsMs[index] + 500L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
            }

            AnimatorSet().apply {
                playTogether(translationAnimator, alphaAnimator, scaleXAnimator, scaleYAnimator)
                startDelay = delaysMs[index]
                start()
                sparkleAnimators.add(this)
            }
        }
    }

    private fun stopSparkleAnimations() {
        sparkleAnimators.forEach { it.cancel() }
        sparkleAnimators.clear()
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
    
    override fun onDestroyView() {
        stopSparkleAnimations()
        loginGlossAnimator?.clear()
        loginGlossAnimator = null
        super.onDestroyView()
        _binding = null
    }
}
