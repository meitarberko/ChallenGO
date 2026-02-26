package com.challengo.app

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.challengo.app.databinding.ActivityMainBinding
import com.challengo.app.di.AppModule
import com.challengo.app.notifications.NotificationScheduler
import com.challengo.app.ui.navigation.AppScreenType
import com.challengo.app.ui.navigation.ScreenTypeResolver
import com.challengo.app.ui.viewmodel.GlobalNavViewModel
import com.challengo.app.ui.viewmodel.ViewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var globalNavViewModel: GlobalNavViewModel
    private var wheelSpinAnimator: ObjectAnimator? = null
    private var wheelPulseJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val firebaseAuth = AppModule.provideFirebaseAuth()
        setupGlobalNavViewModel()
        binding.bottomNavigation.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            globalNavViewModel.syncSession(firebaseAuth.currentUser?.uid)
            val screenType = ScreenTypeResolver.resolve(destination.id)
            val showBottomBar = screenType is AppScreenType.Main && firebaseAuth.currentUser != null
            setBottomBarVisible(showBottomBar)
            globalNavViewModel.refreshChallengeState()
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.homeFragment -> {
                    navController.navigate(R.id.homeFragment)
                    true
                }
                R.id.profileFragment -> {
                    val userId = firebaseAuth.currentUser?.uid ?: return@setOnItemSelectedListener false
                    navController.navigate(R.id.profileFragment, bundleOf("userId" to userId))
                    true
                }
                else -> false
            }
        }

        binding.fabCenterWheel.setOnClickListener {
            navController.navigate(R.id.rollChallengeFragment)
        }

        if (savedInstanceState == null && firebaseAuth.currentUser != null) {
            val navOptions = NavOptions.Builder()
                .setPopUpTo(navController.graph.startDestinationId, true)
                .setLaunchSingleTop(true)
                .build()
            navController.navigate(R.id.homeFragment, null, navOptions)
            NotificationScheduler.scheduleRecurring(applicationContext)
        }
        handleNotificationIntent(intent, navController)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                globalNavViewModel.challengeCycleState.collect { cycleState ->
                    val shouldSpin = cycleState == GlobalNavViewModel.ChallengeCycleState.NO_CHALLENGE
                    binding.fabCenterWheel.alpha = if (shouldSpin) 1f else 0.5f
                    if (shouldSpin) startWheelAttractAnimation() else stopWheelAttractAnimation()
                }
            }
        }
    }

    override fun onDestroy() {
        stopWheelAttractAnimation()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        globalNavViewModel.syncSession(AppModule.provideFirebaseAuth().currentUser?.uid)
        globalNavViewModel.refreshChallengeState()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment ?: return
        handleNotificationIntent(intent, navHostFragment.navController)
    }

    private fun setupGlobalNavViewModel() {
        val db = AppModule.provideDatabase(application)
        val challengeRepo = AppModule.provideChallengeRepository(
            AppModule.provideFirestore(),
            db.challengeDao()
        )
        val userId = AppModule.provideFirebaseAuth().currentUser?.uid.orEmpty()
        globalNavViewModel = ViewModelProvider(
            this,
            ViewModelFactory { GlobalNavViewModel(challengeRepo, userId) }
        )[GlobalNavViewModel::class.java]
    }

    private fun startWheelAttractAnimation() {
        if (wheelSpinAnimator == null) {
            wheelSpinAnimator = ObjectAnimator.ofFloat(binding.fabCenterWheel, "rotation", 0f, 360f).apply {
                duration = 7000L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        } else if (wheelSpinAnimator?.isStarted == false) {
            wheelSpinAnimator?.start()
        }

        if (wheelPulseJob == null) {
            wheelPulseJob = lifecycleScope.launch {
                while (isActive) {
                    binding.fabCenterWheel.animate()
                        .scaleX(1.08f)
                        .scaleY(1.08f)
                        .setDuration(600L)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .withEndAction {
                            binding.fabCenterWheel.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(600L)
                                .setInterpolator(AccelerateDecelerateInterpolator())
                                .start()
                        }
                        .start()
                    delay(4600L)
                }
            }
        }
    }

    private fun stopWheelAttractAnimation() {
        wheelSpinAnimator?.cancel()
        wheelSpinAnimator = null
        wheelPulseJob?.cancel()
        wheelPulseJob = null
        binding.fabCenterWheel.rotation = 0f
        binding.fabCenterWheel.scaleX = 1f
        binding.fabCenterWheel.scaleY = 1f
    }

    private fun setBottomBarVisible(visible: Boolean) {
        binding.bottomNavContainer.isVisible = visible
        binding.wheelRing.isVisible = visible
        binding.bottomNavigation.isVisible = visible
        binding.bottomNavigation.isEnabled = visible
        binding.bottomNavigation.menu.setGroupEnabled(0, visible)
        binding.fabCenterWheel.isVisible = visible
    }

    private fun handleNotificationIntent(
        incomingIntent: android.content.Intent?,
        navController: androidx.navigation.NavController
    ) {
        val destination = incomingIntent?.getStringExtra(com.challengo.app.notifications.NotificationHelper.EXTRA_DESTINATION)
        if (destination.isNullOrBlank()) {
            return
        }
        when (destination) {
            com.challengo.app.data.repository.NotificationRepository.DESTINATION_POST_DETAILS -> {
                val postId = incomingIntent.getStringExtra(com.challengo.app.notifications.NotificationHelper.EXTRA_POST_ID)
                if (!postId.isNullOrBlank()) {
                    navController.navigate(
                        R.id.postDetailsFragment,
                        bundleOf("postId" to postId)
                    )
                }
            }

            com.challengo.app.data.repository.NotificationRepository.DESTINATION_ROLL_CHALLENGE -> {
                navController.navigate(R.id.rollChallengeFragment)
            }
        }
        incomingIntent.removeExtra(com.challengo.app.notifications.NotificationHelper.EXTRA_DESTINATION)
        incomingIntent.removeExtra(com.challengo.app.notifications.NotificationHelper.EXTRA_POST_ID)
    }
}
