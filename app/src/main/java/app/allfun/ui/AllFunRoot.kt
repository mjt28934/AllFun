package app.allfun.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import app.allfun.ui.category.CategoryScreen
import app.allfun.ui.detail.DetailScreen
import app.allfun.ui.downloads.DownloadsScreen
import app.allfun.ui.extensions.ExtensionsScreen
import app.allfun.ui.glass.AFIcons
import app.allfun.ui.glass.GlassLevel
import app.allfun.ui.glass.GlassNav
import app.allfun.ui.glass.GlassNavState
import app.allfun.ui.glass.GlassTab
import app.allfun.ui.glass.LocalGlassNavState
import app.allfun.ui.glass.Section
import app.allfun.ui.glass.SectionScope
import app.allfun.ui.glass.WorldBackdrop
import app.allfun.ui.glass.worldPalette
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
    const val MOVIES = "movies"
    const val BOOKS = "books"
    const val MANGA = "manga-library"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val EXTENSIONS = "extensions?category={category}"
    const val UPDATES = "updates"
    const val DOWNLOADS = "downloads"

    fun extensions(category: ContentCategory? = null) =
        "extensions" + (category?.let { "?category=${it.name}" } ?: "")

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

private val tabs = listOf(
    GlassTab(Routes.MOVIES, "Movies", AFIcons.Clapper, AFIcons.Clapper, Section.MOVIES),
    GlassTab(Routes.BOOKS, "Books", AFIcons.Book, AFIcons.Book, Section.BOOKS),
    GlassTab(Routes.MANGA, "Manga", AFIcons.Manga, AFIcons.Manga, Section.MANGA),
    GlassTab(Routes.LIBRARY, "Library", AFIcons.Bookmark, AFIcons.Bookmark, Section.LIBRARY),
    GlassTab(Routes.SETTINGS, "Settings", AFIcons.Sliders, AFIcons.Sliders, Section.SYSTEM)
)

private val tabRoutes = tabs.map { it.route }.toSet()

private fun sectionForRoute(route: String?): Section = when (route) {
    Routes.BOOKS -> Section.BOOKS
    Routes.MANGA, Routes.MANGA_READER, Routes.NOVEL -> Section.MANGA
    Routes.LIBRARY -> Section.LIBRARY
    Routes.SETTINGS, Routes.EXTENSIONS, Routes.UPDATES, Routes.DOWNLOADS -> Section.SYSTEM
    else -> Section.MOVIES
}

@Composable
fun AllFunRoot(container: AppContainer) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
    AllFunTheme(settings) {
        val nav = rememberNavController()
        val backStackEntry by nav.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val showBar = tabs.any { it.route == currentRoute }
        val reduceMotion = LocalReduceMotion.current
        val navState = remember { GlassNavState() }
        val section = sectionForRoute(currentRoute)

        val openLibrary: () -> Unit = {
            nav.navigate(Routes.LIBRARY) {
                popUpTo(Routes.MOVIES) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }

        fun tabIndex(route: String?): Int = tabs.indexOfFirst { it.route == route }

        // Screen changes are fades; only glass controls spring. Reduced motion keeps the
        // fade and drops every translation and scale.
        val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
            val from = tabIndex(initialState.destination.route)
            val to = tabIndex(targetState.destination.route)
            when {
                reduceMotion -> fadeIn(tween(120))
                from >= 0 && to >= 0 -> {
                    val forward = to > from
                    fadeIn(tween(200)) +
                        slideInHorizontally(tween(240)) { if (forward) it / 10 else -it / 10 }
                }
                else -> fadeIn(tween(200)) +
                    scaleIn(tween(320), initialScale = 0.97f)
            }
        }
        val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
            val from = tabIndex(initialState.destination.route)
            val to = tabIndex(targetState.destination.route)
            when {
                reduceMotion -> fadeOut(tween(120))
                from >= 0 && to >= 0 -> {
                    val forward = to > from
                    fadeOut(tween(160)) +
                        slideOutHorizontally(tween(240)) { if (forward) -it / 14 else it / 14 }
                }
                else -> fadeOut(tween(160)) +
                    scaleOut(tween(240), targetScale = 1.02f)
            }
        }
        val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
            if (reduceMotion) fadeIn(tween(120))
            else fadeIn(tween(200)) + slideInHorizontally(tween(240)) { -it / 12 }
        }
        val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
            if (reduceMotion) fadeOut(tween(120))
            else fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { it / 12 }
        }

        CompositionLocalProvider(LocalGlassNavState provides navState) {
            Box(Modifier.fillMaxSize()) {
                // Base world. Screens draw their own; this keeps the nav's backdrop and
                // the gap between two screens from flashing black.
                WorldBackdrop(section = section, intensity = 0.9f) {
                    NavHost(
                        navController = nav,
                        startDestination = Routes.MOVIES,
                        enterTransition = enterTransition,
                        exitTransition = exitTransition,
                        popEnterTransition = popEnterTransition,
                        popExitTransition = popExitTransition,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable(Routes.MOVIES) {
                            SectionScope(Section.MOVIES) {
                                CategoryScreen(
                                    container = container,
                                    category = ContentCategory.MOVIES,
                                    onOpenMedia = { nav.navigate(Routes.detail(it)) },
                                    onOpenExtensions = { nav.navigate(Routes.extensions(ContentCategory.MOVIES)) },
                                    onOpenLibrary = openLibrary,
                                    onOpenGlobalSearch = { nav.navigate(Routes.SEARCH) }
                                )
                            }
                        }

                        composable(Routes.BOOKS) {
                            SectionScope(Section.BOOKS) {
                                CategoryScreen(
                                    container = container,
                                    category = ContentCategory.BOOKS,
                                    onOpenMedia = { nav.navigate(Routes.detail(it)) },
                                    onOpenExtensions = { nav.navigate(Routes.extensions(ContentCategory.BOOKS)) },
                                    onOpenLibrary = openLibrary,
                                    onOpenGlobalSearch = { nav.navigate(Routes.SEARCH) }
                                )
                            }
                        }

                        composable(Routes.MANGA) {
                            SectionScope(Section.MANGA) {
                                CategoryScreen(
                                    container = container,
                                    category = ContentCategory.MANGA,
                                    onOpenMedia = { nav.navigate(Routes.detail(it)) },
                                    onOpenExtensions = { nav.navigate(Routes.extensions(ContentCategory.MANGA)) },
                                    onOpenLibrary = openLibrary,
                                    onOpenGlobalSearch = { nav.navigate(Routes.SEARCH) }
                                )
                            }
                        }

                        composable(Routes.LIBRARY) {
                            SectionScope(Section.LIBRARY) {
                                LibraryScreen(
                                    container = container,
                                    onOpenMedia = { nav.navigate(Routes.detail(it)) },
                                    onBrowse = { nav.navigate(Routes.extensions()) }
                                )
                            }
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
                            SectionScope(Section.SYSTEM) {
                                GlobalSearchScreen(
                                    container = container,
                                    initialCategory = ContentCategory.fromName(
                                        entry.arguments?.getString("category")
                                    ),
                                    onBack = { nav.popBackStack() },
                                    onOpenMedia = { nav.navigate(Routes.detail(it)) },
                                    onManageExtensions = { nav.navigate(Routes.extensions()) }
                                )
                            }
                        }

                        composable(
                            route = Routes.EXTENSIONS,
                            arguments = listOf(
                                navArgument("category") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            )
                        ) { entry ->
                            SectionScope(Section.SYSTEM) {
                                ExtensionsScreen(
                                    container = container,
                                    initialCategory = ContentCategory.fromName(
                                        entry.arguments?.getString("category")
                                    ),
                                    onBack = { nav.popBackStack() }
                                )
                            }
                        }

                        composable(Routes.UPDATES) {
                            SectionScope(Section.SYSTEM) {
                                UpdatesScreen(
                                    container = container,
                                    onBack = { nav.popBackStack() }
                                )
                            }
                        }

                        composable(Routes.DOWNLOADS) {
                            SectionScope(Section.SYSTEM) {
                                DownloadsScreen(
                                    container = container,
                                    onBack = { nav.popBackStack() },
                                    onOpenMedia = { nav.navigate(Routes.detail(it)) }
                                )
                            }
                        }

                        composable(Routes.SETTINGS) {
                            SectionScope(Section.SYSTEM) {
                                SettingsScreen(
                                    container = container,
                                    onManageExtensions = { nav.navigate(Routes.extensions()) },
                                    onOpenUpdates = { nav.navigate(Routes.UPDATES) },
                                    onOpenDownloads = { nav.navigate(Routes.DOWNLOADS) }
                                )
                            }
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

                AnimatedVisibility(
                    visible = showBar && !navState.hidden,
                    enter = slideInVertically(tween(220)) { it } + fadeIn(tween(180)),
                    exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(140)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    GlassNav(
                        tabs = tabs,
                        currentRoute = currentRoute,
                        onSelect = { tab ->
                            navState.show()
                            nav.navigate(tab.route) {
                                popUpTo(Routes.MOVIES) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    }
}
