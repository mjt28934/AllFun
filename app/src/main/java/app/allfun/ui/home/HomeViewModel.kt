package app.allfun.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allfun.data.AppContainer
import app.allfun.data.content.ContentCategory
import app.allfun.data.content.category
import app.allfun.data.db.ContinueRow
import app.allfun.data.db.LibraryRow
import app.allfun.data.db.SourceEntity
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
        val libraryCount: Int = 0,
        val categoryCounts: Map<ContentCategory, Int> = emptyMap(),
        val enabledSources: Int = 0,
        val unavailableSources: Int = 0,
        val sourcesByCategory: Map<ContentCategory, Int> = emptyMap()
    )

    private val sourceHealth = container.sources.observeAll()
        .map { list ->
            val enabled = list.filter { it.enabled }
            enabled to enabled.count { entity -> !isAvailable(container, entity) }
        }
        .flowOn(Dispatchers.IO)

    val state: StateFlow<HomeState> = combine(
        container.library.observeContinue(),
        container.library.observeLibraryWithProgress(),
        sourceHealth
    ) { continued, library, (enabled, unavailable) ->
        val counts = ContentCategory.entries.associateWith { category ->
            library.count { it.media.kind.category() == category }
        }
        HomeState(
            continueRows = continued.sortedByDescending { it.progressUpdatedAt },
            recentlyAdded = library.sortedByDescending { it.media.addedAt }.take(20),
            libraryCount = library.size,
            categoryCounts = counts,
            enabledSources = enabled.size,
            unavailableSources = unavailable,
            sourcesByCategory = ContentCategory.entries.associateWith { category ->
                enabled.count { it.type.category() == category }
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeState()
    )

    private fun isAvailable(container: AppContainer, entity: SourceEntity): Boolean =
        runCatching { container.sourceHub.isAvailable(entity) }.getOrDefault(true)
}
