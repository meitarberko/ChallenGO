package com.challengo.app.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.challengo.app.databinding.FragmentRollChallengeBinding
import com.challengo.app.di.AppModule
import com.challengo.app.notifications.NotificationScheduler
import com.challengo.app.ui.viewmodel.ChallengeViewModel
import com.challengo.app.ui.viewmodel.ViewModelFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RollChallengeFragment : Fragment() {
    private var _binding: FragmentRollChallengeBinding? = null
    private val binding get() = _binding!!

    private val currentUserId: String? by lazy {
        AppModule.provideFirebaseAuth().currentUser?.uid
    }

    private var countdownJob: Job? = null

    private val viewModel: ChallengeViewModel by viewModels {
        val db = AppModule.provideDatabase(requireContext().applicationContext as android.app.Application)
        val challengeRepo = AppModule.provideChallengeRepository(
            AppModule.provideFirestore(),
            db.challengeDao()
        )
        ViewModelFactory {
            ChallengeViewModel(challengeRepo, currentUserId ?: "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRollChallengeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRoll.setOnClickListener {
            viewModel.rollChallenge()
        }

        applyInsets()
        setupObservers()
    }

    private fun applyInsets() {
        val baseStart = binding.rollScroll.paddingStart
        val baseTop = binding.rollScroll.paddingTop
        val baseEnd = binding.rollScroll.paddingEnd
        val baseBottom = binding.rollScroll.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.rollScroll) { view, insets ->
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

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    viewModel.dailyChallenge.collect { challenge ->
                        challenge?.let {
                            binding.tvChallengeText.text = it.challengeText
                            binding.tvChallengeDesc.text = it.challengeCategory
                            binding.tvHashtag.text = "#${it.challengeHashtag.removePrefix("#")}"
                            binding.btnRoll.visibility = View.GONE
                            startCountdown(it.getRemainingTimeMillis())
                        } ?: run {
                            binding.btnRoll.visibility = View.VISIBLE
                            countdownJob?.cancel()
                            binding.tvCountdown.text = "24:00:00"
                            binding.tvChallengeDesc.text = "Roll to reveal your positive mission for today."
                            binding.tvHashtag.text = "#Challenge"
                        }
                    }
                }
            }
        }

        viewModel.rollState.observe(viewLifecycleOwner, Observer { state ->
            when (state) {
                is com.challengo.app.ui.viewmodel.RollState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnRoll.isEnabled = false
                }
                is com.challengo.app.ui.viewmodel.RollState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnRoll.isEnabled = true
                    state.challenge?.let { challenge ->
                        NotificationScheduler.scheduleChallengeNotDoneReminders(
                            requireContext().applicationContext,
                            currentUserId.orEmpty(),
                            challenge.challengeId,
                            challenge.rollTime
                        )
                    }
                }
                is com.challengo.app.ui.viewmodel.RollState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnRoll.isEnabled = true
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun startCountdown(remainingMillis: Long) {
        countdownJob?.cancel()
        countdownJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            var remaining = remainingMillis
            while (remaining > 0) {
                val hours = remaining / (1000 * 60 * 60)
                val minutes = (remaining % (1000 * 60 * 60)) / (1000 * 60)
                val seconds = (remaining % (1000 * 60)) / 1000
                binding.tvCountdown.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                delay(1000)
                remaining -= 1000
            }
            binding.tvCountdown.text = "Expired"
            viewModel.clearExpiredChallengeIfNeeded()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countdownJob?.cancel()
        _binding = null
    }
}
