package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.runBlocking
import rx.Observable

@Suppress("Unused")
interface CatalogueSource : Source {

    /**
     * An ISO 639-1 compliant language code (two letters in lower case).
     */
    val lang: String

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (!SourceBridge.overrides(this, "fetchPopularManga", Int::class.javaPrimitiveType!!)) {
            throw UnsupportedOperationException("${javaClass.name} does not implement getPopularManga")
        }
        return fetchPopularManga(page).awaitSingleValue()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (!SourceBridge.overrides(this, "fetchLatestUpdates", Int::class.javaPrimitiveType!!)) {
            throw UnsupportedOperationException("${javaClass.name} does not implement getLatestUpdates")
        }
        return fetchLatestUpdates(page).awaitSingleValue()
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        if (!SourceBridge.overrides(
                this,
                "fetchSearchManga",
                Int::class.javaPrimitiveType!!,
                String::class.java,
                FilterList::class.java,
            )
        ) {
            throw UnsupportedOperationException("${javaClass.name} does not implement getSearchManga")
        }
        return fetchSearchManga(page, query, filters).awaitSingleValue()
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val updatedManga = if (fetchDetails) {
            if (!SourceBridge.overrides(this, "fetchMangaDetails", SManga::class.java)) {
                throw UnsupportedOperationException("${javaClass.name} does not implement getMangaUpdate")
            }
            fetchMangaDetails(manga).awaitSingleValue()
        } else {
            manga
        }
        val updatedChapters = if (fetchChapters) {
            if (!SourceBridge.overrides(this, "fetchChapterList", SManga::class.java)) {
                throw UnsupportedOperationException("${javaClass.name} does not implement getMangaUpdate")
            }
            fetchChapterList(manga).awaitSingleValue()
        } else {
            chapters
        }
        return SMangaUpdate(updatedManga, updatedChapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (!SourceBridge.overrides(this, "fetchPageList", SChapter::class.java)) {
            throw UnsupportedOperationException("${javaClass.name} does not implement getPageList")
        }
        return fetchPageList(chapter).awaitSingleValue()
    }

    @Deprecated("Use the suspend API instead", ReplaceWith("getPopularManga"))
    fun fetchPopularManga(page: Int): Observable<MangasPage> = Observable.defer {
        Observable.fromCallable { runBlocking { getPopularManga(page) } }
    }

    @Deprecated("Use the suspend API instead", ReplaceWith("getSearchManga"))
    fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.defer {
        Observable.fromCallable { runBlocking { getSearchManga(page, query, filters) } }
    }

    @Deprecated("Use the suspend API instead", ReplaceWith("getLatestUpdates"))
    fun fetchLatestUpdates(page: Int): Observable<MangasPage> = Observable.defer {
        Observable.fromCallable { runBlocking { getLatestUpdates(page) } }
    }
}
