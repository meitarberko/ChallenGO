package com.challengo.app.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.challengo.app.R
import com.challengo.app.data.repository.NotificationRepository
import com.challengo.app.databinding.FragmentNotificationsBinding
import com.challengo.app.di.AppModule
import com.challengo.app.ui.adapter.NotificationAdapter
import com.challengo.app.ui.viewmodel.NotificationsViewModel
import com.challengo.app.ui.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch

class NotificationsFragment : Fragment() {
    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: NotificationAdapter

    private val currentUserId: String by lazy {
        AppModule.provideFirebaseAuth().currentUser?.uid.orEmpty()
    }

    private val viewModel: NotificationsViewModel by viewModels {
        val notificationRepository = AppModule.provideNotificationRepository(
            AppModule.provideFirestore()
        )
        ViewModelFactory { NotificationsViewModel(notificationRepository, currentUserId) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecycler()
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnClear.setOnClickListener { viewModel.clearAll() }
        observe()
    }

    override fun onStart() {
        super.onStart()
        viewModel.markAllRead()
    }

    private fun setupRecycler() {
        adapter = NotificationAdapter { notification ->
            when (notification.deepLink["destination"]) {
                NotificationRepository.DESTINATION_POST_DETAILS -> {
                    val postId = notification.deepLink["postId"] ?: notification.postId
                    if (!postId.isNullOrBlank()) {
                        findNavController().navigate(
                            R.id.postDetailsFragment,
                            Bundle().apply { putString("postId", postId) }
                        )
                    }
                }

                NotificationRepository.DESTINATION_ROLL_CHALLENGE -> {
                    findNavController().navigate(R.id.rollChallengeFragment)
                }
            }
        }
        binding.rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        binding.rvNotifications.adapter = adapter
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.notifications.collect { items ->
                        adapter.submitList(items)
                        binding.emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        }
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }
        viewModel.error.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrBlank()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                viewModel.consumeError()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
