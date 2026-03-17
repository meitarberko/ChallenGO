package com.challengo.app.ui.fragment

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.challengo.app.R
import com.challengo.app.databinding.FragmentProfileBinding
import com.challengo.app.di.AppModule
import com.challengo.app.ui.adapter.ProfileContentAdapter
import com.challengo.app.ui.adapter.ProfileHeaderState
import com.challengo.app.ui.viewmodel.ProfileViewModel
import com.challengo.app.ui.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var contentAdapter: ProfileContentAdapter
    private var latestUsername: String = ""
    private var latestChallenges: Int = 0
    private var latestProfileImage: String? = null
    private var latestPoints: Int = 0
    private var latestHashtags: List<String> = emptyList()

    private val currentUserId: String? by lazy {
        AppModule.provideFirebaseAuth().currentUser?.uid
    }

    private val userId: String by lazy {
        arguments?.getString("userId") ?: currentUserId ?: ""
    }

    private val isOwnProfile: Boolean by lazy {
        userId == currentUserId
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
            ProfileViewModel(userRepo, postRepo, likeRepo, userId, currentUserId ?: "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        applyInsets()
        setupObservers()
    }

    private fun setupRecyclerView() {
        contentAdapter = ProfileContentAdapter(
            isOwnProfile = isOwnProfile,
            onEditProfileClick = {
                findNavController().navigate(R.id.action_profileFragment_to_editProfileFragment)
            },
            onLogoutClick = {
                AppModule.provideFirebaseAuth().signOut()
                findNavController().navigate(R.id.action_profileFragment_to_loginFragment)
            },
            onPostClick = { post ->
                findNavController().navigate(
                    ProfileFragmentDirections.actionProfileFragmentToPostDetailsFragment(post.id)
                )
            }
        )

        val layoutManager = GridLayoutManager(requireContext(), 3).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return when (contentAdapter.getItemViewType(position)) {
                        ProfileContentAdapter.VIEW_TYPE_POST -> 1
                        else -> 3
                    }
                }
            }
        }

        binding.rvProfileContent.layoutManager = layoutManager
        binding.rvProfileContent.adapter = contentAdapter
        binding.rvProfileContent.addItemDecoration(GridSpacingDecoration(3, dpToPx(8)))
    }

    private fun applyInsets() {
        val baseStart = binding.rvProfileContent.paddingStart
        val baseTop = binding.rvProfileContent.paddingTop
        val baseEnd = binding.rvProfileContent.paddingEnd
        val baseBottom = binding.rvProfileContent.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.rvProfileContent) { view, insets ->
            val system = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = baseStart,
                top = baseTop + system.top,
                right = baseEnd,
                bottom = baseBottom + system.bottom
            )
            insets
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.user.collect { user ->
                user ?: return@collect
                latestUsername = user.username
                latestProfileImage = user.profileImageUri
                submitHeaderState()
            }
        }

        lifecycleScope.launch {
            viewModel.profileStats.collect { stats ->
                latestPoints = stats.points
                latestHashtags = stats.hashtags
                submitHeaderState()
            }
        }

        lifecycleScope.launch {
            viewModel.userPosts.collect { posts ->
                latestChallenges = posts.count {
                    it.challengeName.isNullOrBlank().not() || it.hashtag.isNotBlank()
                }
                contentAdapter.submitPosts(posts)
                submitHeaderState()
            }
        }
    }

    private fun submitHeaderState() {
        val derivedLevel = (latestPoints / 50) + 1
        contentAdapter.submitHeader(
            ProfileHeaderState(
                username = latestUsername,
                level = derivedLevel,
                points = latestPoints,
                challenges = latestChallenges,
                profileImageUri = latestProfileImage,
                hashtags = latestHashtags
            )
        )
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class GridSpacingDecoration(
    private val spanCount: Int,
    private val spacing: Int
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val adapter = parent.adapter as? ProfileContentAdapter ?: return
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return
        if (adapter.getItemViewType(position) != ProfileContentAdapter.VIEW_TYPE_POST) return

        val postIndex = position - 1
        val column = postIndex % spanCount

        outRect.left = spacing - column * spacing / spanCount
        outRect.right = (column + 1) * spacing / spanCount
        outRect.bottom = spacing
        if (postIndex < spanCount) {
            outRect.top = spacing
        }
    }
}

