package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.runBlocking
import rx.Observable

/**
 * A basic interface for creating a source. It could be an online source, a local source, etc...
 */
@Suppress("Unused")
interface Source {

    /**
     * ID for the source. Must be unique.
     */
    val id: Long

    /**
     * Name of the source.
     */
    val name: String

    /**
     * Whether the source has support for latest updates.
     */
    val supportsLatest: Boolean

    /**
     * Returns the list of filters for the source.
     */
    fun getFilterList(): FilterList = FilterList()

    suspend fun getPopularManga(page: Int): MangasPage

    suspend fun getLatestUpdates(page: Int): MangasPage

    suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage

    suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate

    suspend fun getPageList(chapter: SChapter): List<Page>

    @Deprecated("Use the combined suspend API instead", ReplaceWith("getMangaUpdate"))
    fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.defer {
        Observable.fromCallable {
            runBlocking { getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga }
        }
    }

    @Deprecated("Use the combined suspend API instead", ReplaceWith("getMangaUpdate"))
    fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.defer {
        Observable.fromCallable {
            runBlocking { getMangaUpdate(manga, emptyList(), fetchDetails = false, fetchChapters = true).chapters }
        }
    }

    @Deprecated("Use the suspend API instead", ReplaceWith("getPageList"))
    fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.defer {
        Observable.fromCallable { runBlocking { getPageList(chapter) } }
    }
}
