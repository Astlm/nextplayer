package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.settings.screens.cache.CachePreferencesScreen
import kotlinx.serialization.Serializable

@Serializable
object CachePreferencesRoute : NavKey

fun NavBackStack<NavKey>.navigateToCachePreferences() {
    add(CachePreferencesRoute)
}

fun EntryProviderScope<NavKey>.cachePreferencesEntry(onNavigateUp: () -> Unit) {
    entry<CachePreferencesRoute> {
        CachePreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
