package app.allfun.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

/** Enough to be useful without turning the empty search screen into a list. */
private const val MAX_SEARCH_HISTORY = 8

enum class ReaderMode { VERTICAL, PAGED }
enum class ThemeMode { SYSTEM, DARK, LIGHT }
enum class LibraryView { GRID, LIST }

/** Accent used when Material You colour extraction is off. */
enum class AccentPalette(val label: String) {
    VIOLET("Violet"),
    OCEAN("Ocean"),
    EMERALD("Emerald"),
    AMBER("Amber"),
    ROSE("Rose")
}

/** Corner radius scale shared by cards, sheets, buttons and dialogs. */
enum class CornerStyle(val label: String) {
    ROUNDED("Rounded"),
    SOFT("Soft"),
    SHARP("Sharp")
}

/** App text scale applied on top of the system font size. */
enum class AppTextSize(val label: String, val scale: Float) {
    SYSTEM("System", 1f),
    COMPACT("Compact", 0.92f),
    LARGE("Large", 1.12f),
    EXTRA_LARGE("Extra large", 1.25f)
}

/** How wide each library poster is; the grid keeps its adaptive column count. */
enum class LibraryGrid(val label: String, val minWidth: Int) {
    COMPACT("Compact", 104),
    COMFORTABLE("Comfortable", 132),
    LARGE("Large", 168)
}

/**
 * How much of the glass system runs. Every level still looks like glass; High adds the
 * grain and the sweep, Lite drops both and shortens the shadows for old devices.
 */
enum class GlassQuality(val label: String) {
    HIGH("High"),
    STANDARD("Standard"),
    LITE("Lite")
}

enum class LibrarySort(val label: String) {
    RECENTLY_UPDATED("Recently updated"),
    RECENTLY_ADDED("Recently added"),
    RECENTLY_OPENED("Recently opened"),
    TITLE("Title A-Z"),
    AUTHOR("Author A-Z")
}
enum class MangaFit(val label: String) {
    WIDTH("Fit width"),
    HEIGHT("Fit height"),
    WHOLE_PAGE("Whole page")
}
enum class ReadingDirection(val label: String) {
    LTR("Left to right"),
    RTL("Right to left (manga)")
}
enum class NovelTheme(val label: String) {
    DARK("Dark"),
    SEPIA("Sepia"),
    LIGHT("Light")
}

/** Seconds skipped by the player's rewind and forward buttons. */
enum class SeekStep(val label: String, val seconds: Int) {
    FIVE("5 s", 5),
    TEN("10 s", 10),
    THIRTY("30 s", 30)
}

enum class RepositoryKind { MANGA, NOVEL }

/** A user-added repository URL. Built-in repositories are not stored here. */
data class RepositoryConfig(val kind: RepositoryKind, val url: String)

data class Settings(
    val readerMode: ReaderMode = ReaderMode.VERTICAL,
    val novelFontSize: Int = 100,
    val novelLineSpacing: Float = 1.72f,
    val novelTheme: NovelTheme = NovelTheme.DARK,
    val dynamicColor: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.DARK,
    /** True-black backgrounds and cards for OLED displays. */
    val amoledBlack: Boolean = false,
    val accent: AccentPalette = AccentPalette.VIOLET,
    val cornerStyle: CornerStyle = CornerStyle.ROUNDED,
    val appTextSize: AppTextSize = AppTextSize.SYSTEM,
    /** Glass rendering budget: High, Standard or Lite. */
    val glassQuality: GlassQuality = GlassQuality.STANDARD,
    val libraryView: LibraryView = LibraryView.GRID,
    val libraryGrid: LibraryGrid = LibraryGrid.COMFORTABLE,
    val librarySort: LibrarySort = LibrarySort.RECENTLY_UPDATED,
    val mangaFit: MangaFit = MangaFit.WIDTH,
    val readingDirection: ReadingDirection = ReadingDirection.LTR,
    val reduceMotion: Boolean = false,
    /** Keep the screen awake while a chapter or novel is open. */
    val readerKeepScreenOn: Boolean = true,
    val resumePlayback: Boolean = true,
    val subtitlesEnabled: Boolean = true,
    /** Move to the next episode when one finishes. */
    val autoPlayNext: Boolean = true,
    val seekStep: SeekStep = SeekStep.TEN,
    val autoCheckUpdates: Boolean = true,
    val notifyUpdates: Boolean = true,
    /** Most recent global-search terms, newest first. */
    val searchHistory: List<String> = emptyList(),
    val repositories: List<RepositoryConfig> = emptyList(),
    /** Preferred provider per title: "CATEGORY|normalised title" -> source id. */
    val preferredSources: Map<String, String> = emptyMap(),
    /** Only download files while connected to Wi-Fi or Ethernet. */
    val downloadWifiOnly: Boolean = true,
    /** How many downloads may transfer at once. */
    val concurrentDownloads: Int = 2,
    /** True while the user has paused the whole queue, like Komikku's pause action. */
    val downloadQueuePaused: Boolean = false
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val readerMode = stringPreferencesKey("reader_mode")
        val novelFontSize = intPreferencesKey("novel_font_size")
        val novelLineSpacing = floatPreferencesKey("novel_line_spacing")
        val novelTheme = stringPreferencesKey("novel_theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val themeMode = stringPreferencesKey("theme_mode")
        val amoledBlack = booleanPreferencesKey("amoled_black")
        val accent = stringPreferencesKey("accent_palette")
        val cornerStyle = stringPreferencesKey("corner_style")
        val appTextSize = stringPreferencesKey("app_text_size")
        val glassQuality = stringPreferencesKey("glass_quality")
        val libraryView = stringPreferencesKey("library_view")
        val libraryGrid = stringPreferencesKey("library_grid")
        val librarySort = stringPreferencesKey("library_sort")
        val mangaFit = stringPreferencesKey("manga_fit")
        val readingDirection = stringPreferencesKey("reading_direction")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val readerKeepScreenOn = booleanPreferencesKey("reader_keep_screen_on")
        val resumePlayback = booleanPreferencesKey("resume_playback")
        val subtitlesEnabled = booleanPreferencesKey("subtitles_enabled")
        val autoPlayNext = booleanPreferencesKey("auto_play_next")
        val seekStep = stringPreferencesKey("seek_step")
        val autoCheckUpdates = booleanPreferencesKey("auto_check_updates")
        val notifyUpdates = booleanPreferencesKey("notify_updates")
        val searchHistory = stringPreferencesKey("search_history")
        val repositories = stringPreferencesKey("repositories")
        val preferredSources = stringPreferencesKey("preferred_sources")
        val downloadWifiOnly = booleanPreferencesKey("download_wifi_only")
        val concurrentDownloads = intPreferencesKey("concurrent_downloads")
        val downloadQueuePaused = booleanPreferencesKey("download_queue_paused")
    }

    val settings: Flow<Settings> = context.dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs ->
            Settings(
                readerMode = prefs[Keys.readerMode].toEnum(ReaderMode.VERTICAL),
                novelFontSize = prefs[Keys.novelFontSize] ?: 100,
                novelLineSpacing = (prefs[Keys.novelLineSpacing] ?: 1.72f).coerceIn(1.2f, 2.4f),
                novelTheme = prefs[Keys.novelTheme].toEnum(NovelTheme.DARK),
                dynamicColor = prefs[Keys.dynamicColor] ?: true,
                themeMode = prefs[Keys.themeMode].toEnum(ThemeMode.DARK),
                amoledBlack = prefs[Keys.amoledBlack] ?: false,
                accent = prefs[Keys.accent].toEnum(AccentPalette.VIOLET),
                cornerStyle = prefs[Keys.cornerStyle].toEnum(CornerStyle.ROUNDED),
                appTextSize = prefs[Keys.appTextSize].toEnum(AppTextSize.SYSTEM),
                glassQuality = prefs[Keys.glassQuality].toEnum(GlassQuality.STANDARD),
                libraryView = prefs[Keys.libraryView].toEnum(LibraryView.GRID),
                libraryGrid = prefs[Keys.libraryGrid].toEnum(LibraryGrid.COMFORTABLE),
                librarySort = prefs[Keys.librarySort].toEnum(LibrarySort.RECENTLY_UPDATED),
                mangaFit = prefs[Keys.mangaFit].toEnum(MangaFit.WIDTH),
                readingDirection = prefs[Keys.readingDirection].toEnum(ReadingDirection.LTR),
                reduceMotion = prefs[Keys.reduceMotion] ?: false,
                readerKeepScreenOn = prefs[Keys.readerKeepScreenOn] ?: true,
                resumePlayback = prefs[Keys.resumePlayback] ?: true,
                subtitlesEnabled = prefs[Keys.subtitlesEnabled] ?: true,
                autoPlayNext = prefs[Keys.autoPlayNext] ?: true,
                seekStep = prefs[Keys.seekStep].toEnum(SeekStep.TEN),
                autoCheckUpdates = prefs[Keys.autoCheckUpdates] ?: true,
                notifyUpdates = prefs[Keys.notifyUpdates] ?: true,
                searchHistory = parseSearchHistory(prefs[Keys.searchHistory]),
                repositories = parseRepositories(prefs[Keys.repositories]),
                preferredSources = parsePreferredSources(prefs[Keys.preferredSources]),
                downloadWifiOnly = prefs[Keys.downloadWifiOnly] ?: true,
                concurrentDownloads = (prefs[Keys.concurrentDownloads] ?: 2).coerceIn(1, 4),
                downloadQueuePaused = prefs[Keys.downloadQueuePaused] ?: false
            )
        }

    suspend fun setReaderMode(mode: ReaderMode) {
        context.dataStore.edit { it[Keys.readerMode] = mode.name }
    }

    suspend fun setNovelFontSize(size: Int) {
        context.dataStore.edit { it[Keys.novelFontSize] = size.coerceIn(70, 180) }
    }

    suspend fun setNovelLineSpacing(value: Float) {
        context.dataStore.edit { it[Keys.novelLineSpacing] = value.coerceIn(1.2f, 2.4f) }
    }

    suspend fun setNovelTheme(theme: NovelTheme) {
        context.dataStore.edit { it[Keys.novelTheme] = theme.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.dynamicColor] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.themeMode] = mode.name }
    }

    suspend fun setAmoledBlack(enabled: Boolean) {
        context.dataStore.edit { it[Keys.amoledBlack] = enabled }
    }

    suspend fun setAccent(palette: AccentPalette) {
        context.dataStore.edit { it[Keys.accent] = palette.name }
    }

    suspend fun setCornerStyle(style: CornerStyle) {
        context.dataStore.edit { it[Keys.cornerStyle] = style.name }
    }

    suspend fun setAppTextSize(size: AppTextSize) {
        context.dataStore.edit { it[Keys.appTextSize] = size.name }
    }

    suspend fun setGlassQuality(quality: GlassQuality) {
        context.dataStore.edit { it[Keys.glassQuality] = quality.name }
    }

    suspend fun setLibraryView(view: LibraryView) {
        context.dataStore.edit { it[Keys.libraryView] = view.name }
    }

    suspend fun setLibraryGrid(grid: LibraryGrid) {
        context.dataStore.edit { it[Keys.libraryGrid] = grid.name }
    }

    suspend fun setLibrarySort(sort: LibrarySort) {
        context.dataStore.edit { it[Keys.librarySort] = sort.name }
    }

    suspend fun setMangaFit(fit: MangaFit) {
        context.dataStore.edit { it[Keys.mangaFit] = fit.name }
    }

    suspend fun setReadingDirection(direction: ReadingDirection) {
        context.dataStore.edit { it[Keys.readingDirection] = direction.name }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        context.dataStore.edit { it[Keys.reduceMotion] = enabled }
    }

    suspend fun setReaderKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[Keys.readerKeepScreenOn] = enabled }
    }

    suspend fun setResumePlayback(enabled: Boolean) {
        context.dataStore.edit { it[Keys.resumePlayback] = enabled }
    }

    suspend fun setSubtitlesEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.subtitlesEnabled] = enabled }
    }

    suspend fun setAutoPlayNext(enabled: Boolean) {
        context.dataStore.edit { it[Keys.autoPlayNext] = enabled }
    }

    suspend fun setSeekStep(step: SeekStep) {
        context.dataStore.edit { it[Keys.seekStep] = step.name }
    }

    suspend fun setAutoCheckUpdates(enabled: Boolean) {
        context.dataStore.edit { it[Keys.autoCheckUpdates] = enabled }
    }

    suspend fun setNotifyUpdates(enabled: Boolean) {
        context.dataStore.edit { it[Keys.notifyUpdates] = enabled }
    }

    suspend fun setDownloadWifiOnly(enabled: Boolean) {
        context.dataStore.edit { it[Keys.downloadWifiOnly] = enabled }
    }

    suspend fun setConcurrentDownloads(value: Int) {
        context.dataStore.edit { it[Keys.concurrentDownloads] = value.coerceIn(1, 4) }
    }

    /** Pausing the queue is persisted, so a restart does not silently resume downloads. */
    suspend fun setDownloadQueuePaused(paused: Boolean) {
        context.dataStore.edit { it[Keys.downloadQueuePaused] = paused }
    }

    /** Records a completed search; newest first, no duplicates, capped at 8 entries. */
    suspend fun addSearchTerm(term: String) {
        val clean = term.trim()
        if (clean.isBlank()) return
        context.dataStore.edit { prefs ->
            val existing = parseSearchHistory(prefs[Keys.searchHistory])
                .filterNot { it.equals(clean, ignoreCase = true) }
            prefs[Keys.searchHistory] = (listOf(clean) + existing)
                .take(MAX_SEARCH_HISTORY)
                .joinToString("\n")
        }
    }

    suspend fun removeSearchTerm(term: String) {
        context.dataStore.edit { prefs ->
            val existing = parseSearchHistory(prefs[Keys.searchHistory])
                .filterNot { it.equals(term, ignoreCase = true) }
            prefs[Keys.searchHistory] = existing.joinToString("\n")
        }
    }

    suspend fun clearSearchHistory() {
        context.dataStore.edit { it.remove(Keys.searchHistory) }
    }

    suspend fun setRepositories(repositories: List<RepositoryConfig>) {
        context.dataStore.edit {
            it[Keys.repositories] = repositories
                .distinctBy { repo -> "${repo.kind}:${repo.url}" }
                .joinToString("\n") { repo -> "${repo.kind.name}|${repo.url}" }
        }
    }

    /** Remembers the provider a user picked for one title. */
    suspend fun setPreferredSource(key: String, sourceId: String) {
        context.dataStore.edit { prefs ->
            val current = parsePreferredSources(prefs[Keys.preferredSources]).toMutableMap()
            current[key] = sourceId
            prefs[Keys.preferredSources] = encodePreferredSources(current)
        }
    }

    /** Writes every preference at once; used when restoring a backup. */
    suspend fun apply(settings: Settings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.readerMode] = settings.readerMode.name
            prefs[Keys.novelFontSize] = settings.novelFontSize.coerceIn(70, 180)
            prefs[Keys.novelLineSpacing] = settings.novelLineSpacing.coerceIn(1.2f, 2.4f)
            prefs[Keys.novelTheme] = settings.novelTheme.name
            prefs[Keys.dynamicColor] = settings.dynamicColor
            prefs[Keys.themeMode] = settings.themeMode.name
            prefs[Keys.amoledBlack] = settings.amoledBlack
            prefs[Keys.accent] = settings.accent.name
            prefs[Keys.cornerStyle] = settings.cornerStyle.name
            prefs[Keys.appTextSize] = settings.appTextSize.name
            prefs[Keys.libraryView] = settings.libraryView.name
            prefs[Keys.libraryGrid] = settings.libraryGrid.name
            prefs[Keys.librarySort] = settings.librarySort.name
            prefs[Keys.mangaFit] = settings.mangaFit.name
            prefs[Keys.readingDirection] = settings.readingDirection.name
            prefs[Keys.reduceMotion] = settings.reduceMotion
            prefs[Keys.readerKeepScreenOn] = settings.readerKeepScreenOn
            prefs[Keys.resumePlayback] = settings.resumePlayback
            prefs[Keys.subtitlesEnabled] = settings.subtitlesEnabled
            prefs[Keys.autoPlayNext] = settings.autoPlayNext
            prefs[Keys.seekStep] = settings.seekStep.name
            prefs[Keys.autoCheckUpdates] = settings.autoCheckUpdates
            prefs[Keys.notifyUpdates] = settings.notifyUpdates
            prefs[Keys.downloadWifiOnly] = settings.downloadWifiOnly
            prefs[Keys.concurrentDownloads] = settings.concurrentDownloads.coerceIn(1, 4)
            prefs[Keys.downloadQueuePaused] = settings.downloadQueuePaused
            prefs[Keys.repositories] = settings.repositories
                .distinctBy { repo -> "${repo.kind}:${repo.url}" }
                .joinToString("\n") { repo -> "${repo.kind.name}|${repo.url}" }
            prefs[Keys.preferredSources] = encodePreferredSources(settings.preferredSources)
        }
    }

    /** Current values, for callers that are not collecting the flow (backup, export). */
    suspend fun current(): Settings = settings.first()

    private fun parseSearchHistory(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(MAX_SEARCH_HISTORY)
            .toList()
    }

    private fun parseRepositories(raw: String?): List<RepositoryConfig> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val kind = line.substringBefore('|', "").let { value ->
                RepositoryKind.entries.firstOrNull { it.name == value }
            } ?: return@mapNotNull null
            val url = line.substringAfter('|', "").trim()
            if (url.isBlank()) null else RepositoryConfig(kind, url)
        }.toList()
    }

    private fun parsePreferredSources(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return buildMap {
            raw.lineSequence().forEach { line ->
                val parts = line.split('|')
                if (parts.size < 3) return@forEach
                val key = parts[0].trim()
                val sourceId = parts[2].trim()
                if (key.isBlank() || sourceId.isBlank()) return@forEach
                put(key, sourceId)
            }
        }
    }

    private fun encodePreferredSources(values: Map<String, String>): String =
        values.entries
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .distinctBy { it.key }
            .joinToString("\n") { entry -> "${entry.key}|${entry.value}" }

    private inline fun <reified T : Enum<T>> String?.toEnum(fallback: T): T =
        this?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: fallback
}
