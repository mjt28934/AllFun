package app.allfun.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import app.allfun.ui.extensions.ExtensionsScreen
import app.allfun.ui.library.LibraryScreen
import app.allfun.ui.player.PlayerScreen
import app.allfun.ui.reader.MangaReaderScreen
import app.allfun.ui.reader.NovelReaderScreen
import app.allfun.ui.search.GlobalSearchScreen
import app.allfun.ui.settings.SettingsScreen
import app.allfun.ui.theme.AllFunTheme
import app.allfun.ui.theme.LocalReduceMotion
import app.allfun.ui.theme.Motion
import app.allfun.ui.update.UpdatesScreen
import app.allfun.ui.downloads.DownloadsScreen
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

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

/**
 * Bottom navigation is exactly the three entertainment categories, the Library and
 * Settings. Extensions, individual providers and repositories never appear here.
 */
private val tabs = listOf(
    Tab(Routes.MOVIES, "Movies", Icons.Outlined.Movie, Icons.Filled.Movie),
    Tab(
        Routes.BOOKS,
        "Books",
        Icons.AutoMirrored.Outlined.MenuBook,
        Icons.AutoMirrored.Filled.MenuBook
    ),
    Tab(Routes.MANGA, "Manga", Icons.Outlined.AutoStories, Icons.Filled.AutoStories),
    Tab(
        Routes.LIBRARY,
        "Library",
        Icons.Outlined.CollectionsBookmark,
        Icons.Filled.CollectionsBookmark
    ),
    Tab(Routes.SETTINGS, "Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
)

private val tabRoutes = tabs.map { it.route }.toSet()

/**
 * The app's own bottom bar: a floating, softly bordered surface with one tinted pill that
 * glides to the selected destination. It is built here instead of Material's NavigationBar
 * so the indicator can actually travel, and so the bar reads as a piece of the content
 * rather than a system chrome strip.
 */
@Composable
private fun AllFunBottomBar(currentRoute: String?, onSelect: (Tab) -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val selectedIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            ),
            shadowElevation = 12.dp
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                val itemWidth = maxWidth / tabs.size
                val indicatorWidth = itemWidth - 10.dp
                val target = itemWidth * selectedIndex + 5.dp
                val offset by animateDpAsState(
                    targetValue = target,
                    animationSpec = if (reduceMotion) snap() else Motion.settle(),
                    label = "tabPill"
                )
                // The indicator covers the icon and label together, so the selected tab
                // reads as one object instead of a circle floating above text.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = offset)
                        .width(indicatorWidth)
                        .height(50.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f)
                                )
                            )
                        )
                )
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    tabs.forEach { tab ->
                        TabItem(
                            tab = tab,
                            selected = tab.route == currentRoute,
                            onClick = { onSelect(tab) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(
    tab: Tab,
    selected: Boolean,
    onClick: () -> Unit
) {
    val reduceMotion = LocalReduceMotion.current
    val haptic = LocalHapticFeedback.current
    val tint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = if (reduceMotion) snap() else tween(Motion.FAST),
        label = "tabTint"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected && !reduceMotion) 1.08f else 1f,
        animationSpec = if (reduceMotion) snap() else Motion.bounce(),
        label = "tabScale"
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.74f,
        animationSpec = if (reduceMotion) snap() else tween(Motion.MEDIUM),
        label = "tabLabelAlpha"
    )
    // Tabs show no ripple/hover rectangle: the moving pill is the only selection
    // feedback, so no grey box flashes under a finger or the mouse.
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = {
                    if (!selected) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onClick()
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (selected) tab.selectedIcon else tab.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer { alpha = labelAlpha }
        )
    }
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
        // Section shortcuts on the category screens jump to the Library tab with the same
        // state behaviour as the bottom bar, so they never stack duplicate destinations.
        val openLibrary: () -> Unit = {
            nav.navigate(Routes.LIBRARY) {
                popUpTo(Routes.MOVIES) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }

        // Tabs sit in a fixed left-to-right order, so the motion can follow the direction
        // of travel the way a pager does: the incoming section fades in while sliding in
        // from the side it lives on, and the outgoing one leaves the opposite way.
        fun tabIndex(route: String?): Int = tabs.indexOfFirst { it.route == route }

        val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
            val from = tabIndex(initialState.destination.route)
            val to = tabIndex(targetState.destination.route)
            when {
                reduceMotion -> EnterTransition.None
                from >= 0 && to >= 0 -> {
                    val forward = to > from
                    fadeIn(tween(Motion.MEDIUM, easing = Motion.EaseOut)) +
                        slideInHorizontally(tween(Motion.MEDIUM, easing = Motion.EaseOut)) {
                            if (forward) it / 2 else -it / 2
                        } +
                        scaleIn(
                            animationSpec = tween(Motion.SLOW, easing = Motion.EaseOut),
                            initialScale = 0.97f
                        )
                }
                // Detail, search and reader screens open like a card being lifted out of
                // the list: a short scale from just under full size, plus a fade.
                else -> fadeIn(tween(Motion.SLOW, easing = Motion.EaseOut)) +
                    scaleIn(
                        animationSpec = tween(Motion.EMPHASIZED, easing = Motion.Emphasized),
                        initialScale = 0.92f
                    )
            }
        }
        val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
            val from = tabIndex(initialState.destination.route)
            val to = tabIndex(targetState.destination.route)
            when {
                reduceMotion -> ExitTransition.None
                from >= 0 && to >= 0 -> {
                    val forward = to > from
                    fadeOut(tween(Motion.MEDIUM, easing = Motion.EaseOut)) +
                        slideOutHorizontally(tween(Motion.MEDIUM, easing = Motion.EaseOut)) {
                            if (forward) -it / 2 else it / 2
                        } +
                        scaleOut(
                            animationSpec = tween(Motion.SLOW, easing = Motion.EaseOut),
                            targetScale = 0.97f
                        )
                }
                else -> fadeOut(tween(Motion.MEDIUM, easing = Motion.EaseOut)) +
                    scaleOut(
                        animationSpec = tween(Motion.SLOW, easing = Motion.Emphasized),
                        targetScale = 1.04f
                    )
            }
        }
        val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
            if (reduceMotion) EnterTransition.None
            else fadeIn(tween(Motion.MEDIUM, easing = Motion.EaseOut)) +
                slideInHorizontally(tween(Motion.MEDIUM, easing = Motion.EaseOut)) { -it / 8 } +
                scaleIn(
                    animationSpec = tween(Motion.SLOW, easing = Motion.EaseOut),
                    initialScale = 0.97f
                )
        }
        val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
            if (reduceMotion) ExitTransition.None
            else fadeOut(tween(Motion.FAST)) +
                slideOutHorizontally(tween(Motion.MEDIUM, easing = Motion.EaseOut)) { it / 6 } +
                scaleOut(
                    animationSpec = tween(Motion.SLOW, easing = Motion.EaseOut),
                    targetScale = 1.02f
                )
        }

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showBar) {
                    AllFunBottomBar(
                        currentRoute = currentRoute,
                        onSelect = { tab ->
                            nav.navigate(tab.route) {
                                popUpTo(Routes.MOVIES) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = Routes.MOVIES,
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding())
            ) {
                composable(Routes.MOVIES) {
                    CategoryScreen(
                        container = container,
                        category = ContentCategory.MOVIES,
                        onOpenMedia = { nav.navigate(Routes.detail(it)) },
                        onOpenExtensions = { nav.navigate(Routes.extensions(ContentCategory.MOVIES)) },
                        onOpenLibrary = openLibrary,
                        onOpenGlobalSearch = { nav.navigate(Routes.SEARCH) }
                    )
                }

                composable(Routes.BOOKS) {
                    CategoryScreen(
                        container = container,
                        category = ContentCategory.BOOKS,
                        onOpenMedia = { nav.navigate(Routes.detail(it)) },
                        onOpenExtensions = { nav.navigate(Routes.extensions(ContentCategory.BOOKS)) },
                        onOpenLibrary = openLibrary,
                        onOpenGlobalSearch = { nav.navigate(Routes.SEARCH) }
                    )
                }

                composable(Routes.MANGA) {
                    CategoryScreen(
                        container = container,
                        category = ContentCategory.MANGA,
                        onOpenMedia = { nav.navigate(Routes.detail(it)) },
                        onOpenExtensions = { nav.navigate(Routes.extensions(ContentCategory.MANGA)) },
                        onOpenLibrary = openLibrary,
                        onOpenGlobalSearch = { nav.navigate(Routes.SEARCH) }
                    )
                }

                composable(Routes.LIBRARY) {
                    LibraryScreen(
                        container = container,
                        onOpenMedia = { nav.navigate(Routes.detail(it)) },
                        onBrowse = { nav.navigate(Routes.extensions()) }
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
                        onManageExtensions = { nav.navigate(Routes.extensions()) }
                    )
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
                    ExtensionsScreen(
                        container = container,
                        initialCategory = ContentCategory.fromName(
                            entry.arguments?.getString("category")
                        ),
                        onBack = { nav.popBackStack() }
                    )
                }

                composable(Routes.UPDATES) {
                    UpdatesScreen(
                        container = container,
                        onBack = { nav.popBackStack() }
                    )
                }

                composable(Routes.DOWNLOADS) {
                    DownloadsScreen(
                        container = container,
                        onBack = { nav.popBackStack() },
                        onOpenMedia = { nav.navigate(Routes.detail(it)) }
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        container = container,
                        onManageExtensions = { nav.navigate(Routes.extensions()) },
                        onOpenUpdates = { nav.navigate(Routes.UPDATES) },
                        onOpenDownloads = { nav.navigate(Routes.DOWNLOADS) }
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
