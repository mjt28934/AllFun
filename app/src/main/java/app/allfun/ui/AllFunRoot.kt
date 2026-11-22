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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.MenuBook
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
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
    Tab(Routes.BOOKS, "Books", Icons.AutoMirrored.Outlined.MenuBook, Icons.Filled.MenuBook),
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
 * The app's own bottom bar: a single tinted pill slides to the selected destination while
 * icons and labels cross-fade colour. It is built here instead of Material's
 * NavigationBar so the indicator can actually move with the selection.
 */
@Composable
private fun AllFunBottomBar(currentRoute: String?, onSelect: (Tab) -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val selectedIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
        ) {
            val itemWidth = maxWidth / tabs.size
            val pillWidth = 52.dp
            val target = itemWidth * selectedIndex + (itemWidth - pillWidth) / 2
            val offset by animateDpAsState(
                targetValue = target,
                animationSpec = if (reduceMotion) snap()
                else tween(Motion.SLOW, easing = Motion.EaseInOut),
                label = "tabPill"
            )
            // The indicator sits behind the icon only: the label stays outside it, so a
            // scaled label at a large font size can never be clipped by the pill. Its y
            // follows the icon, which moves as the label grows with the system font scale.
            val labelHeight = with(LocalDensity.current) { 11.sp.toDp() } * 1.45f
            val iconTop = (64.dp - (22.dp + 3.dp + labelHeight)) / 2
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = offset, y = iconTop - 4.dp)
                    .width(pillWidth)
                    .height(30.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
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

@Composable
private fun RowScope.TabItem(
    tab: Tab,
    selected: Boolean,
    onClick: () -> Unit
) {
    val reduceMotion = LocalReduceMotion.current
    val tint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = if (reduceMotion) snap() else tween(Motion.FAST),
        label = "tabTint"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected && !reduceMotion) 1.08f else 1f,
        animationSpec = tween(Motion.FAST, easing = Motion.EaseOut),
        label = "tabScale"
    )
    // Tabs show no ripple/hover rectangle: the moving ring is the only selection
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
                onClick = onClick
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
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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

        val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
            when {
                reduceMotion -> EnterTransition.None
                // Switching tabs cross-fades in place: no sideways travel between peers.
                initialState.destination.route in tabRoutes &&
                    targetState.destination.route in tabRoutes ->
                    fadeIn(tween(Motion.MEDIUM, easing = Motion.EaseOut))
                else -> fadeIn(tween(Motion.MEDIUM, easing = Motion.EaseOut)) +
                    slideInHorizontally(tween(Motion.MEDIUM, easing = Motion.EaseOut)) {
                        it / 6
                    }
            }
        }
        val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
            if (reduceMotion) ExitTransition.None else fadeOut(tween(Motion.FAST))
        }
        val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
            if (reduceMotion) EnterTransition.None
            else fadeIn(tween(Motion.MEDIUM, easing = Motion.EaseOut)) +
                slideInHorizontally(tween(Motion.MEDIUM, easing = Motion.EaseOut)) { -it / 8 }
        }
        val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
            if (reduceMotion) ExitTransition.None
            else fadeOut(tween(Motion.FAST)) +
                slideOutHorizontally(tween(Motion.MEDIUM, easing = Motion.EaseOut)) { it / 6 }
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
                        onOpenExtensions = { nav.navigate(Routes.extensions(ContentCategory.MOVIES)) }
                    )
                }

                composable(Routes.BOOKS) {
                    CategoryScreen(
                        container = container,
                        category = ContentCategory.BOOKS,
                        onOpenMedia = { nav.navigate(Routes.detail(it)) },
                        onOpenExtensions = { nav.navigate(Routes.extensions(ContentCategory.BOOKS)) }
                    )
                }

                composable(Routes.MANGA) {
                    CategoryScreen(
                        container = container,
                        category = ContentCategory.MANGA,
                        onOpenMedia = { nav.navigate(Routes.detail(it)) },
                        onOpenExtensions = { nav.navigate(Routes.extensions(ContentCategory.MANGA)) }
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
