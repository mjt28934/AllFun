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

enum class ReaderMode { VERTICAL, PAGED }
enum class ThemeMode { SYSTEM, DARK, LIGHT }
enum class LibraryView { GRID, LIST }
enum class LibrarySort(val label: String) {
    RECENTLY_UPDATED("Recently updated"),
    RECENTLY_ADDED("Recently added"),
    TITLE("Title A–Z"),
    AUTHOR("Author A–Z")
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

/** Home sections the user can reorder or hide. Keys are stable across versions. */
enum class HomeSection(val title: String, val key: String) {
    CONTINUE_READING("Continue reading", "continue_reading"),
    CONTINUE_WATCHING("Continue watching", "continue_watching"),
    RECENTLY_ADDED("Recently added", "recently_added"),
    RECENTLY_OPENED("Recently opened", "recently_opened"),
    RECENTLY_UPDATED("Recently updated", "recently_updated"),
    MANGA_FROM_SOURCES("Manga from your sources", "recs_manga"),
    NOVELS_FROM_SOURCES("Novels from your sources", "recs_novels"),
    VIDEO_FROM_SOURCES("Movies & TV from your sources", "recs_video");

    companion object {
        fun fromKey(key: String): HomeSection? = entries.firstOrNull { it.key == key }
    }
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
    val libraryView: LibraryView = LibraryView.GRID,
    val librarySort: LibrarySort = LibrarySort.RECENTLY_UPDATED,
    val mangaFit: MangaFit = MangaFit.WIDTH,
    val readingDirection: ReadingDirection = ReadingDirection.LTR,
    val reduceMotion: Boolean = false,
    val resumePlayback: Boolean = true,
    val subtitlesEnabled: Boolean = true,
    val autoCheckUpdates: Boolean = true,
    val notifyUpdates: Boolean = true,
    val homeSections: List<HomeSection> = HomeSection.entries,
    val repositories: List<RepositoryConfig> = emptyList(),
    /** Preferred provider per title: "CATEGORY|normalised title" -> source id. */
    val preferredSources: Map<String, String> = emptyMap()
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val readerMode = stringPreferencesKey("reader_mode")
        val novelFontSize = intPreferencesKey("novel_font_size")
        val novelLineSpacing = floatPreferencesKey("novel_line_spacing")
        val novelTheme = stringPreferencesKey("novel_theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val themeMode = stringPreferencesKey("theme_mode")
        val libraryView = stringPreferencesKey("library_view")
        val librarySort = stringPreferencesKey("library_sort")
        val mangaFit = stringPreferencesKey("manga_fit")
        val readingDirection = stringPreferencesKey("reading_direction")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val resumePlayback = booleanPreferencesKey("resume_playback")
        val subtitlesEnabled = booleanPreferencesKey("subtitles_enabled")
        val autoCheckUpdates = booleanPreferencesKey("auto_check_updates")
        val notifyUpdates = booleanPreferencesKey("notify_updates")
        val homeSections = stringPreferencesKey("home_sections")
        val repositories = stringPreferencesKey("repositories")
        val preferredSources = stringPreferencesKey("preferred_sources")
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
                libraryView = prefs[Keys.libraryView].toEnum(LibraryView.GRID),
                librarySort = prefs[Keys.librarySort].toEnum(LibrarySort.RECENTLY_UPDATED),
                mangaFit = prefs[Keys.mangaFit].toEnum(MangaFit.WIDTH),
                readingDirection = prefs[Keys.readingDirection].toEnum(ReadingDirection.LTR),
                reduceMotion = prefs[Keys.reduceMotion] ?: false,
                resumePlayback = prefs[Keys.resumePlayback] ?: true,
                subtitlesEnabled = prefs[Keys.subtitlesEnabled] ?: true,
                autoCheckUpdates = prefs[Keys.autoCheckUpdates] ?: true,
                notifyUpdates = prefs[Keys.notifyUpdates] ?: true,
                homeSections = parseHomeSections(prefs[Keys.homeSections]),
                repositories = parseRepositories(prefs[Keys.repositories]),
                preferredSources = parsePreferredSources(prefs[Keys.preferredSources])
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

    suspend fun setLibraryView(view: LibraryView) {
        context.dataStore.edit { it[Keys.libraryView] = view.name }
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

    suspend fun setResumePlayback(enabled: Boolean) {
        context.dataStore.edit { it[Keys.resumePlayback] = enabled }
    }

    suspend fun setSubtitlesEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.subtitlesEnabled] = enabled }
    }

    suspend fun setAutoCheckUpdates(enabled: Boolean) {
        context.dataStore.edit { it[Keys.autoCheckUpdates] = enabled }
    }

    suspend fun setNotifyUpdates(enabled: Boolean) {
        context.dataStore.edit { it[Keys.notifyUpdates] = enabled }
    }

    suspend fun setHomeSections(sections: List<HomeSection>) {
        context.dataStore.edit {
            it[Keys.homeSections] = sections.distinct().joinToString(",") { section -> section.key }
        }
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
            prefs[Keys.libraryView] = settings.libraryView.name
            prefs[Keys.librarySort] = settings.librarySort.name
            prefs[Keys.mangaFit] = settings.mangaFit.name
            prefs[Keys.readingDirection] = settings.readingDirection.name
            prefs[Keys.reduceMotion] = settings.reduceMotion
            prefs[Keys.resumePlayback] = settings.resumePlayback
            prefs[Keys.subtitlesEnabled] = settings.subtitlesEnabled
            prefs[Keys.autoCheckUpdates] = settings.autoCheckUpdates
            prefs[Keys.notifyUpdates] = settings.notifyUpdates
            prefs[Keys.homeSections] = settings.homeSections.distinct()
                .joinToString(",") { section -> section.key }
            prefs[Keys.repositories] = settings.repositories
                .distinctBy { repo -> "${repo.kind}:${repo.url}" }
                .joinToString("\n") { repo -> "${repo.kind.name}|${repo.url}" }
            prefs[Keys.preferredSources] = encodePreferredSources(settings.preferredSources)
        }
    }

    /** Current values, for callers that are not collecting the flow (backup, export). */
    suspend fun current(): Settings = settings.first()

    private fun parseHomeSections(raw: String?): List<HomeSection> {
        if (raw == null) return HomeSection.entries
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { HomeSection.fromKey(it.trim()) }.distinct()
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
