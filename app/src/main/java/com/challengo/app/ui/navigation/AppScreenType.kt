package com.challengo.app.ui.navigation

sealed class AppScreenType {
    data object Auth : AppScreenType()
    data object Main : AppScreenType()
}