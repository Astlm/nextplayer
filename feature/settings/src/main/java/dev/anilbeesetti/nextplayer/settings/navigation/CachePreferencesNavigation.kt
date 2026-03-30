package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navOptions
import dev.anilbeesetti.nextplayer.settings.screens.cache.CachePreferencesScreen

const val cachePreferencesNavigationRoute = "cache_preferences_route"

fun NavController.navigateToCachePreferences(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    navigate(cachePreferencesNavigationRoute, navOptions)
}

fun NavGraphBuilder.cachePreferencesScreen(onNavigateUp: () -> Unit) {
    composable(route = cachePreferencesNavigationRoute) {
        CachePreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
