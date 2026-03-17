package com.challengo.app.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.challengo.app.R
import com.challengo.app.databinding.FragmentHomeBinding
import com.challengo.app.di.AppModule
import com.challengo.app.ui.adapter.PostAdapter
import com.challengo.app.ui.viewmodel.GlobalNavViewModel
import com.challengo.app.ui.viewmodel.HomeViewModel
import com.challengo.app.ui.viewmodel.ViewModelFactory
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var postAdapter: PostAdapter
    private val currentUserId: String? by lazy {
        AppModule.provideFirebaseAuth().currentUser?.uid
    }
    
    private val viewModel: HomeViewModel by viewModels {
        val firestore = AppModule.provideFirestore()
        val db = AppModule.provideDatabase(requireContext().applicationContext as android.app.Application)
        val postRepo = AppModule.providePostRepository(
            firestore,
            db.postDao()
        )
        val quoteRepo = AppModule.provideQuoteRepository(
            db.quoteDao()
        )
        val likeRepo = AppModule.provideLikeRepository(
            firestore,
            db.postLikeDao(),
            AppModule.provideNotificationRepository(firestore),
            postRepo
        )
        val notificationRepo = AppModule.provideNotificationRepository(firestore)
        ViewModelFactory { HomeViewModel(postRepo, quoteRepo, likeRepo, notificationRepo, currentUserId ?: "") }
    }

    private val globalNavViewModel: GlobalNavViewModel by activityViewModels {
        val db = AppModule.provideDatabase(requireContext().applicationContext as android.app.Application)
        val challengeRepo = AppModule.provideChallengeRepository(
            AppModule.provideFirestore(),
            db.challengeDao()
        )
        ViewModelFactory { GlobalNavViewModel(challengeRepo, currentUserId ?: "") }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupObservers()

        binding.fabCreatePost.visibility = View.VISIBLE

        binding.fabCreatePost.setOnClickListener {
            if (globalNavViewModel.challengeCycleState.value == GlobalNavViewModel.ChallengeCycleState.ACTIVE_CAN_POST) {
                findNavController().navigate(R.id.action_homeFragment_to_createPostFragment)
            }
        }

        binding.notificationContainer.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_notificationsFragment)
        }
    }
    
    private fun setupRecyclerView() {
        postAdapter = PostAdapter(
            currentUserId = currentUserId,
            onPostClick = { post ->
                findNavController().navigate(
                    HomeFragmentDirections.actionHomeFragmentToPostDetailsFragment(postId = post.id)
                )
            },
            onProfileClick = { userId ->
                findNavController().navigate(
                    HomeFragmentDirections.actionHomeFragmentToProfileFragment(userId = userId)
                )
            },
            onLikeClick = { post ->
                viewModel.toggleLike(post)
            },
            onCommentClick = { post ->
                findNavController().navigate(
                    HomeFragmentDirections.actionHomeFragmentToPostDetailsFragment(postId = post.id)
                )
            }
        )
        
        binding.rvPosts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPosts.adapter = postAdapter
    }
    
    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.posts.collect { posts ->
                        postAdapter.submitList(posts)
                        binding.tvEmptyState.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.likedState.collect { likedMap ->
                        postAdapter.submitLikedState(likedMap)
                    }
                }

                launch {
                    viewModel.quote.collect { quote ->
                        quote?.let {
                            binding.tvQuote.text = "\"${it.text}\""
                            binding.tvQuoteAuthor.text = "- ${it.author}"
                        }
                    }
                }

                launch {
                    viewModel.notificationCount.collect { count ->
                        if (count > 0) {
                            binding.tvNotificationBadge.visibility = View.VISIBLE
                            binding.tvNotificationBadge.text = if (count > 99) "99+" else count.toString()
                        } else {
                            binding.tvNotificationBadge.visibility = View.GONE
                        }
                    }
                }

                launch {
                    globalNavViewModel.challengeCycleState.collect { cycleState ->
                        val canCreatePost = cycleState == GlobalNavViewModel.ChallengeCycleState.ACTIVE_CAN_POST
                        binding.fabCreatePost.isEnabled = canCreatePost
                        binding.fabCreatePost.isClickable = canCreatePost
                        binding.fabCreatePost.alpha = if (canCreatePost) 1f else 0.35f
                        binding.challengeWaitContainer.visibility =
                            if (cycleState == GlobalNavViewModel.ChallengeCycleState.DONE_WAITING) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    globalNavViewModel.remainingWaitMillis.collect { millis ->
                        binding.tvChallengeWaitTimer.text = formatDuration(millis)
                    }
                }

                launch {
                    viewModel.likeErrors.collect { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}
