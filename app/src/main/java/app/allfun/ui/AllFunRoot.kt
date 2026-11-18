package app.allfun.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.allfun.data.AppContainer
import app.allfun.data.content.ContentCategory
import app.allfun.data.model.MediaKind
import app.allfun.data.settings.Settings
import app.allfun.ui.browse.BrowseScreen
import app.allfun.ui.category.CategoryScreen
import app.allfun.ui.detail.DetailScreen
import app.allfun.ui.extensions.ExtensionsScreen
import app.allfun.ui.home.HomeScreen
import app.allfun.ui.library.LibraryScreen
import app.allfun.ui.player.PlayerScreen
import app.allfun.ui.reader.MangaReaderScreen
import app.allfun.ui.reader.NovelReaderScreen
import app.allfun.ui.search.GlobalSearchScreen
import app.allfun.ui.settings.SettingsScreen
import app.allfun.ui.theme.AllFunTheme
import app.allfun.ui.theme.LocalReduceMotion
import app.allfun.ui.update.UpdatesScreen

object Routes {
    const val HOME = "home"
    const val MOVIES = "movies"
    const val BOOKS = "books"
    const val MANGA = "manga-library"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val EXTENSIONS = "extensions"
    const val UPDATES = "updates"
    const val SOURCES = "sources"

    const val SEARCH = "search?category={category}"
    fun search(category: ContentCategory? = null) =
        "search" + (category?.let { "?category=${it.name}" } ?: "")

    const val DETAIL = "detail/{mediaId}?from={from}"
    fun detail(mediaId: String) = "detail/$mediaId?from=library"

    const val MANGA_READER = "manga/{mediaId}?episodeId={episodeId}"
    fun manga(mediaId: String, episodeId: String?) =
        "manga/$mediaId" + (episodeId?.let { "?episodeId=$it" } ?: "")

    const val NOVEL = "novel/{mediaId}?episodeId={episodeId}"
    fun novel(mediaId: String, episodeId: String?) =
        "novel/$mediaId" + (episodeId?.let { "?episodeId=$it" } ?: "")

    const val PLAYER = "player/{mediaId}?episodeId={episodeId}"
    fun player(mediaId: String, episodeId: String?) =
        "player/$mediaId" + (episodeId?.let { "?episodeId=$it" } ?: "")
}

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

/**
 * Bottom navigation is exactly the three entertainment categories plus Home and
 * Settings. Extensions, individual providers and repositories never appear here.
 */
private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Tab(Routes.MOVIES, "Movies", Icons.Outlined.Movie, Icons.Filled.Movie),
    Tab(Routes.BOOKS, "Books", Icons.AutoMirrored.Outlined.MenuBook, Icons.Filled.MenuBook),
    Tab(Routes.MANGA, "Manga", Icons.Outlined.AutoStories, Icons.Filled.AutoStories),
    Tab(Routes.SETTINGS, "Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
)

@Composable
fun AllFunRoot(container: AppContainer) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
    AllFunTheme(settings) {
        val nav = rememberNavController()
        val backStackEntry by nav.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val showBar = tabs.any { it.route == currentRoute }
        val reduceMotion = LocalReduceMotion.current

        val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
            if (reduceMotion) EnterTransition.None else fadeIn(tween(180))
        }
        val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
            if (reduceMotion) ExitTransition.None else fadeOut(tween(140))
        }

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showBar) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        nav.navigate(tab.route) {
                                            popUpTo(Routes.HOME) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.icon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = Routes.HOME,
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = enterTransition,
                popExitTransition = exitTransition,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding())
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        container = container,
                        onOpenMedia = { nav.navigate(Routes.detail(it)) },
                        onOpenLibrary = { nav.navigate(Routes.LIBRARY) },
                        onSearch = { nav.navigate(Routes.search()) },
                        onOpenCategory = { category ->
                            nav.navigate(
                                when (category) {
                                    ContentCategory.MOVIES -> Routes.MOVIES
                                    ContentCategory.BOOKS -> Routes.BOOKS
                                    ContentCategory.MANGA -> Routes.MANGA
                                }
                            )
                        },
                        onManageSources = { nav.navigate(Routes.SOURCES) }
                    )
                }

                composable(Routes.MOVIES) {
                    CategoryScreen(
                        container = container,
                        category = ContentCategory.MOVIES,
                        onOpenMedia = { nav.navigate(Routes.detail(it)) },
                        onManageSources = { nav.navigate(Routes.SOURCES) }
                    )
                }

                composable(Routes.BOOKS) {
                    CategoryScreen(
                        container = container,
                        category = ContentCategory.BOOKS,
                        onOpenMedia = { nav.navigate(Routes.detail(it)) },
                        onManageSources = { nav.navigate(Routes.SOURCES) }
                    )
                }

                composable(Routes.MANGA) {
                    CategoryScreen(
                        container = container,
                        category = ContentCategory.MANGA,
                        onOpenMedia = { nav.navigate(Routes.detail(it)) },
                        onManageSources = { nav.navigate(Routes.SOURCES) }
                    )
                }

                composable(Routes.LIBRARY) {
                    LibraryScreen(
                        container = container,
                        onOpenMedia = { nav.navigate(Routes.detail(it)) },
                        onBrowse = { nav.navigate(Routes.SOURCES) }
                    )
                }

                composable(
                    route = Routes.SEARCH,
                    arguments = listOf(
                        navArgument("category") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { entry ->
                    GlobalSearchScreen(
                        container = container,
                        initialCategory = ContentCategory.fromName(
                            entry.arguments?.getString("category")
                        ),
                        onBack = { nav.popBackStack() },
                        onOpenMedia = { nav.navigate(Routes.detail(it)) },
                        onManageSources = { nav.navigate(Routes.SOURCES) }
                    )
                }

                composable(Routes.SOURCES) {
                    BrowseScreen(
                        container = container,
                        onOpenMedia = { nav.navigate(Routes.detail(it)) },
                        onManageExtensions = { nav.navigate(Routes.EXTENSIONS) },
                        onBack = { nav.popBackStack() }
                    )
                }

                composable(Routes.EXTENSIONS) {
                    ExtensionsScreen(
                        container = container,
                        onBack = { nav.popBackStack() }
                    )
                }

                composable(Routes.UPDATES) {
                    UpdatesScreen(
                        container = container,
                        onBack = { nav.popBackStack() }
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        container = container,
                        onManageExtensions = { nav.navigate(Routes.EXTENSIONS) },
                        onManageSources = { nav.navigate(Routes.SOURCES) },
                        onOpenUpdates = { nav.navigate(Routes.UPDATES) }
                    )
                }

                composable(
                    route = Routes.DETAIL,
                    arguments = listOf(
                        navArgument("mediaId") { type = NavType.StringType },
                        navArgument("from") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { entry ->
                    val mediaId = entry.arguments?.getString("mediaId").orEmpty()
                    DetailScreen(
                        container = container,
                        mediaId = mediaId,
                        onBack = { nav.popBackStack() },
                        onSwitchSource = { newId ->
                            nav.navigate(Routes.detail(newId)) {
                                popUpTo(entry.destination.id) { inclusive = true }
                            }
                        },
                        onOpen = { id, episodeId, kind ->
                            when (kind) {
                                MediaKind.MANGA -> nav.navigate(Routes.manga(id, episodeId))
                                MediaKind.NOVEL -> nav.navigate(Routes.novel(id, episodeId))
                                else -> nav.navigate(Routes.player(id, episodeId))
                            }
                        }
                    )
                }

                composable(
                    route = Routes.MANGA_READER,
                    arguments = listOf(
                        navArgument("mediaId") { type = NavType.StringType },
                        navArgument("episodeId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { entry ->
                    MangaReaderScreen(
                        container = container,
                        mediaId = entry.arguments?.getString("mediaId").orEmpty(),
                        startEpisodeId = entry.arguments?.getString("episodeId")?.takeIf { it.isNotBlank() },
                        onBack = { nav.popBackStack() }
                    )
                }

                composable(
                    route = Routes.NOVEL,
                    arguments = listOf(
                        navArgument("mediaId") { type = NavType.StringType },
                        navArgument("episodeId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { entry ->
                    NovelReaderScreen(
                        container = container,
                        mediaId = entry.arguments?.getString("mediaId").orEmpty(),
                        startEpisodeId = entry.arguments?.getString("episodeId")?.takeIf { it.isNotBlank() },
                        onBack = { nav.popBackStack() }
                    )
                }

                composable(
                    route = Routes.PLAYER,
                    arguments = listOf(
                        navArgument("mediaId") { type = NavType.StringType },
                        navArgument("episodeId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { entry ->
                    PlayerScreen(
                        container = container,
                        mediaId = entry.arguments?.getString("mediaId").orEmpty(),
                        startEpisodeId = entry.arguments?.getString("episodeId")?.takeIf { it.isNotBlank() },
                        onBack = { nav.popBackStack() }
                    )
                }
            }
        }
    }
}
