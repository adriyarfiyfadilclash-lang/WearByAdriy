package com.adriy.wearbyadriy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.adriy.wearbyadriy.ui.auth.AuthFlow
import com.adriy.wearbyadriy.ui.auth.SetNewPasswordScreen
import com.adriy.wearbyadriy.ui.components.LocalAppViewModel
import com.adriy.wearbyadriy.ui.components.OfflineBanner
import com.adriy.wearbyadriy.ui.home.HomeScreen
import com.adriy.wearbyadriy.ui.looks.LookBuilderScreen
import com.adriy.wearbyadriy.ui.looks.LookDetailScreen
import com.adriy.wearbyadriy.ui.looks.LooksScreen
import com.adriy.wearbyadriy.ui.settings.AiSettingsScreen
import com.adriy.wearbyadriy.ui.settings.ProfileSettingsScreen
import com.adriy.wearbyadriy.ui.settings.SettingsScreen
import com.adriy.wearbyadriy.ui.style.StyleScreen
import com.adriy.wearbyadriy.ui.theme.WearTheme
import com.adriy.wearbyadriy.ui.wardrobe.ItemDetailScreen
import com.adriy.wearbyadriy.ui.wardrobe.ItemEditorScreen
import com.adriy.wearbyadriy.ui.wardrobe.WardrobeScreen
import io.github.jan.supabase.auth.status.SessionStatus

object Routes {
    const val HOME = "home"
    const val LOOKS = "looks"
    const val WARDROBE = "wardrobe"
    const val STYLE = "style"
    const val SETTINGS = "settings"
    const val SETTINGS_PROFILE = "settings/profile"
    const val SETTINGS_AI = "settings/ai"
    const val ITEM_NEW = "item/new"
    const val ITEM = "item/{id}"
    const val ITEM_EDIT = "item/{id}/edit"
    const val LOOK = "look/{id}"
    const val LOOK_NEW = "look/new"
    const val LOOK_EDIT_ITEMS = "look/{id}/items"
    fun item(id: String) = "item/$id"
    fun itemEdit(id: String) = "item/$id/edit"
    fun look(id: String) = "look/$id"
    fun lookItems(id: String) = "look/$id/items"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Tab(Routes.LOOKS, "Other Looks", Icons.Outlined.Collections, Icons.Filled.Collections),
    Tab(Routes.WARDROBE, "Wardrobe", Icons.Outlined.Checkroom, Icons.Filled.Checkroom),
    Tab(Routes.STYLE, "Style", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
)

@Composable
fun WearRoot(vm: AppViewModel) {
    val theme by vm.prefs.theme.collectAsStateWithLifecycle()
    val status by vm.session.collectAsStateWithLifecycle()
    val recovery by vm.recovery.collectAsStateWithLifecycle()

    WearTheme(theme) {
        CompositionLocalProvider(LocalAppViewModel provides vm) {
            when (status) {
                is SessionStatus.Initializing -> Splash()
                is SessionStatus.NotAuthenticated -> AuthFlow()
                // A failed token refresh (usually offline) keeps the stored session; data calls will
                // show their own errors and an expired session ends in NotAuthenticated.
                is SessionStatus.RefreshFailure, is SessionStatus.Authenticated ->
                    if (recovery) SetNewPasswordScreen(onDone = { vm.setRecovery(false) }) else MainShell(vm)
                else -> Splash()
            }
        }
    }
}

@Composable
private fun Splash() {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("WearByAdriy", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun MainShell(vm: AppViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val online by vm.online.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { it.route == route }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                tabs.forEach { t ->
                    val selected = route == t.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { nav.goTab(t.route) },
                        icon = { Icon(if (selected) t.selectedIcon else t.icon, null) },
                        label = { Text(t.label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                    )
                }
            }
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                NavHost(nav, startDestination = Routes.HOME) {
                    composable(Routes.HOME) { HomeScreen(nav) }
                    composable(Routes.LOOKS) { LooksScreen(nav) }
                    composable(Routes.WARDROBE) { WardrobeScreen(nav) }
                    composable(Routes.STYLE) { StyleScreen(nav) }
                    composable(Routes.SETTINGS) { SettingsScreen(nav) }
                    composable(Routes.SETTINGS_PROFILE) { ProfileSettingsScreen(nav) }
                    composable(Routes.SETTINGS_AI) { AiSettingsScreen(nav) }
                    composable(Routes.ITEM_NEW) { ItemEditorScreen(nav, null) }
                    composable(Routes.ITEM, listOf(navArgument("id") { type = NavType.StringType })) {
                        ItemDetailScreen(nav, it.arguments?.getString("id").orEmpty())
                    }
                    composable(Routes.ITEM_EDIT, listOf(navArgument("id") { type = NavType.StringType })) {
                        ItemEditorScreen(nav, it.arguments?.getString("id"))
                    }
                    composable(Routes.LOOK_NEW) { LookBuilderScreen(nav, null) }
                    composable(Routes.LOOK, listOf(navArgument("id") { type = NavType.StringType })) {
                        LookDetailScreen(nav, it.arguments?.getString("id").orEmpty())
                    }
                    composable(Routes.LOOK_EDIT_ITEMS, listOf(navArgument("id") { type = NavType.StringType })) {
                        LookBuilderScreen(nav, it.arguments?.getString("id"))
                    }
                }
            }
            if (!online) OfflineBanner()
        }
    }
}

fun NavHostController.goTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Shown when the APK was built without Supabase settings. Nothing is faked. */
@Composable
fun ConfigMissingScreen() {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding().padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(40.dp))
            Text("WearByAdriy", style = MaterialTheme.typography.headlineMedium)
            Text("Aplikasi ini belum terhubung ke server.", style = MaterialTheme.typography.titleMedium)
            Text(
                "APK ini dibuat tanpa SUPABASE_URL dan SUPABASE_ANON_KEY, jadi login dan penyimpanan cloud tidak bisa dipakai. " +
                    "Isi kedua nilai itu di local.properties atau di GitHub Secrets, lalu build ulang. Lihat README untuk langkahnya.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
