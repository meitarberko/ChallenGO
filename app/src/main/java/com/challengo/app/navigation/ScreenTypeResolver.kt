package com.challengo.app.ui.navigation

import com.challengo.app.R

object ScreenTypeResolver {
    private val authDestinations = setOf(
        R.id.loginFragment,
        R.id.registerFragment
    )

    fun resolve(destinationId: Int): AppScreenType {
        return if (destinationId in authDestinations) {
            AppScreenType.Auth
        } else {
            AppScreenType.Main
        }
    }
}