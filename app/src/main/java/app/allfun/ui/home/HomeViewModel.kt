package app.allfun.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allfun.data.AppContainer
import app.allfun.data.content.ContentCategory
import app.allfun.data.content.category
import app.allfun.data.db.ContinueRow
import app.allfun.data.db.LibraryRow
import app.allfun.data.db.SourceEntity
import app.allfun.data.settings.HomeSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Home is the unified starting point: one search entry, one continue row per medium and
 * one card for each of the three entertainment categories. It deliberately contains no
 * per-source shelves; providers are infrastructure, not destinations.
 */
class HomeViewModel(container: AppContainer) : ViewModel() {

    data class HomeState(
        val continueRows: List<ContinueRow> = emptyList(),
        val recentlyAdded: List<LibraryRow> = emptyList(),
        val recentlyUpdated: List<LibraryRow> = emptyList(),
        val recentlyOpened: List<LibraryRow> = emptyList(),
        val favorites: List<LibraryRow> = emptyList(),
        val libraryCount: Int = 0,
        val categoryCounts: Map<ContentCategory, Int> = emptyMap(),
        val enabledSources: Int = 0,
        val unavailableSources: Int = 0,
        val sourcesByCategory: Map<ContentCategory, Int> = emptyMap(),
        val unavailableByCategory: Map<ContentCategory, Int> = emptyMap(),
        val homeSections: List<HomeSection> = HomeSection.entries
    )

    private data class SourceHealth(
        val enabled: Int,
        val unavailable: Int,
        val availableByCategory: Map<ContentCategory, Int>,
        val unavailableByCategory: Map<ContentCategory, Int>
    )

    private val sourceHealth = container.sources.observeAll()
        .map { list ->
            val enabled = list.filter { it.enabled }
            val available = enabled.filter { entity -> isAvailable(container, entity) }
            SourceHealth(
                enabled = enabled.size,
                unavailable = enabled.size - available.size,
                availableByCategory = ContentCategory.entries.associateWith { category ->
                    available.count { it.type.category() == category }
                },
                unavailableByCategory = ContentCategory.entries.associateWith { category ->
                    (enabled - available.toSet()).count { it.type.category() == category }
                }
            )
        }
        .flowOn(Dispatchers.IO)

    private val homeSections = container.settings.settings
        .map { it.homeSections }
        .flowOn(Dispatchers.IO)

    val state: StateFlow<HomeState> = combine(
        container.library.observeContinue(),
        container.library.observeLibraryWithProgress(),
        sourceHealth,
        homeSections
    ) { continued, library, health, sections ->
        val counts = ContentCategory.entries.associateWith { category ->
            library.count { it.media.kind.category() == category }
        }
        HomeState(
            continueRows = continued.sortedByDescending { it.progressUpdatedAt },
            recentlyAdded = library.sortedByDescending { it.media.addedAt }.take(20),
            recentlyUpdated = library
                .filter { it.media.updatedAt > it.media.addedAt }
                .sortedByDescending { it.media.updatedAt }
                .take(20),
            recentlyOpened = library
                .filter { it.media.lastOpenedAt != null }
                .sortedByDescending { it.media.lastOpenedAt }
                .take(20),
            favorites = library
                .filter { it.media.favorite }
                .sortedByDescending { it.media.updatedAt }
                .take(20),
            libraryCount = library.size,
            categoryCounts = counts,
            enabledSources = health.enabled,
            unavailableSources = health.unavailable,
            sourcesByCategory = health.availableByCategory,
            unavailableByCategory = health.unavailableByCategory,
            homeSections = sections
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeState()
    )

    private fun isAvailable(container: AppContainer, entity: SourceEntity): Boolean =
        runCatching { container.sourceHub.isAvailable(entity) }.getOrDefault(true)
}
