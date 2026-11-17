package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.HostServices
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceBridge
import eu.kanade.tachiyomi.source.awaitSingleValue
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.security.MessageDigest

/**
 * A simple implementation for sources from a website.
 */
@Suppress("UNUSED", "UnusedReceiverParameter", "DEPRECATION")
abstract class HttpSource : CatalogueSource {

    /**
     * Network service.
     */
    protected val network: NetworkHelper = HostServices.networkHelper

    /**
     * Base url of the website without the trailing slash, like: http://mysite.com
     */
    abstract val baseUrl: String

    open fun getHomeUrl(): String = baseUrl

    /**
     * Version id used to generate the source id. If the site completely changes and urls are
     * incompatible, you may increase this value and it'll be considered as a new source.
     */
    open val versionId: Int = 1

    /**
     * Id of the source, generated from name/language/versionId.
     */
    override val id: Long by lazy {
        val key = "${name.lowercase()}/${lang.lowercase()}/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        var result = 0L
        for (i in 0 until 8) {
            result = result or ((bytes[i].toLong() and 0xFF) shl (8 * i))
        }
        result and Long.MAX_VALUE
    }

    /**
     * Headers used for requests.
     */
    val headers: Headers by lazy { headersBuilder().build() }

    /**
     * Default network client for doing requests.
     */
    open val client: OkHttpClient
        get() = network.client

    /**
     * Headers builder for requests. Implementations can override this method for custom headers.
     */
    protected open fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", HostServices.userAgent)

    override fun toString(): String = name

    override fun getFilterList(): FilterList = FilterList()

    // ---------------------------------------------------------------------------------------
    // Suspend API
    // ---------------------------------------------------------------------------------------

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (SourceBridge.overrides(this, "fetchPopularManga", Int::class.javaPrimitiveType!!)) {
            return fetchPopularManga(page).awaitSingleValue()
        }
        return client.newCall(popularMangaRequest(page)).awaitSuccess().use { popularMangaParse(it) }
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (SourceBridge.overrides(this, "fetchLatestUpdates", Int::class.javaPrimitiveType!!)) {
            return fetchLatestUpdates(page).awaitSingleValue()
        }
        return client.newCall(latestUpdatesRequest(page)).awaitSuccess().use { latestUpdatesParse(it) }
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        if (SourceBridge.overrides(
                this,
                "fetchSearchManga",
                Int::class.javaPrimitiveType!!,
                String::class.java,
                FilterList::class.java,
            )
        ) {
            return fetchSearchManga(page, query, filters).awaitSingleValue()
        }
        return client.newCall(searchMangaRequest(page, query, filters)).awaitSuccess()
            .use { searchMangaParse(it) }
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val updatedManga = if (fetchDetails) fetchMangaDetails(manga).awaitSingleValue() else manga
        val updatedChapters = if (fetchChapters) fetchChapterList(manga).awaitSingleValue() else chapters
        return SMangaUpdate(updatedManga, updatedChapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (SourceBridge.overrides(this, "fetchPageList", SChapter::class.java)) {
            return fetchPageList(chapter).awaitSingleValue()
        }
        return client.newCall(pageListRequest(chapter)).awaitSuccess().use { pageListParse(it) }
    }

    /**
     * Returns the image url for the provided [page]. The function is only called if [Page.imageUrl] is null.
     */
    open suspend fun getImageUrl(page: Page): String {
        if (SourceBridge.overrides(this, "fetchImageUrl", Page::class.java)) {
            return fetchImageUrl(page).awaitSingleValue()
        }
        return client.newCall(imageUrlRequest(page)).awaitSuccess().use { imageUrlParse(it) }
    }

    // ---------------------------------------------------------------------------------------
    // Legacy Rx API
    // ---------------------------------------------------------------------------------------

    override fun fetchPopularManga(page: Int): Observable<MangasPage> =
        client.newCall(popularMangaRequest(page)).asObservableSuccess()
            .map { response -> response.use { popularMangaParse(it) } }

    protected open fun popularMangaRequest(page: Int): Request =
        throw UnsupportedOperationException("${javaClass.name} does not implement popularMangaRequest")

    protected open fun popularMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException("${javaClass.name} does not implement popularMangaParse")

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        client.newCall(searchMangaRequest(page, query, filters)).asObservableSuccess()
            .map { response -> response.use { searchMangaParse(it) } }

    protected open fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        throw UnsupportedOperationException("${javaClass.name} does not implement searchMangaRequest")

    protected open fun searchMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException("${javaClass.name} does not implement searchMangaParse")

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> =
        client.newCall(latestUpdatesRequest(page)).asObservableSuccess()
            .map { response -> response.use { latestUpdatesParse(it) } }

    protected open fun latestUpdatesRequest(page: Int): Request =
        throw UnsupportedOperationException("${javaClass.name} does not implement latestUpdatesRequest")

    protected open fun latestUpdatesParse(response: Response): MangasPage =
        throw UnsupportedOperationException("${javaClass.name} does not implement latestUpdatesParse")

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        client.newCall(mangaDetailsRequest(manga)).asObservableSuccess()
            .map { response -> response.use { mangaDetailsParse(it) } }

    open fun mangaDetailsRequest(manga: SManga): Request = GET(getMangaUrl(manga), headers)

    protected open fun mangaDetailsParse(response: Response): SManga =
        throw UnsupportedOperationException("${javaClass.name} does not implement mangaDetailsParse")

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> =
        client.newCall(chapterListRequest(manga)).asObservableSuccess()
            .map { response -> response.use { chapterListParse(it) } }

    protected open fun chapterListRequest(manga: SManga): Request = GET(getMangaUrl(manga), headers)

    protected open fun chapterListParse(response: Response): List<SChapter> =
        throw UnsupportedOperationException("${javaClass.name} does not implement chapterListParse")

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> =
        client.newCall(pageListRequest(chapter)).asObservableSuccess()
            .map { response -> response.use { pageListParse(it) } }

    protected open fun pageListRequest(chapter: SChapter): Request = GET(getChapterUrl(chapter), headers)

    protected open fun pageListParse(response: Response): List<Page> =
        throw UnsupportedOperationException("${javaClass.name} does not implement pageListParse")

    open fun fetchImageUrl(page: Page): Observable<String> =
        client.newCall(imageUrlRequest(page)).asObservableSuccess()
            .map { response -> response.use { imageUrlParse(it) } }

    protected open fun imageUrlRequest(page: Page): Request =
        GET(page.url.takeIf { it.startsWith("http") } ?: (baseUrl + page.url), headers)

    protected open fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException("${javaClass.name} does not implement imageUrlParse")

    protected open fun imageRequest(page: Page): Request =
        GET(page.imageUrl?.takeIf { it.isNotBlank() } ?: page.url, headers)

    // ---------------------------------------------------------------------------------------
    // URL helpers
    // ---------------------------------------------------------------------------------------

    fun SChapter.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    fun SManga.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    private fun getUrlWithoutDomain(orig: String): String {
        val httpUrl = orig.toHttpUrlOrNull() ?: return orig
        val path = httpUrl.encodedPath
        return if (httpUrl.encodedQuery != null) "$path?${httpUrl.encodedQuery}" else path
    }

    open fun getMangaUrl(manga: SManga): String {
        return if (manga.url.startsWith("http")) manga.url else baseUrl + manga.url
    }

    open fun getChapterUrl(chapter: SChapter): String {
        return if (chapter.url.startsWith("http")) chapter.url else baseUrl + chapter.url
    }

    @Deprecated("All modifications should be done when constructing the chapter")
    open fun prepareNewChapter(chapter: SChapter, manga: SManga) {
    }

    private fun GET(url: String, headers: Headers): Request =
        Request.Builder().url(url).headers(headers).build()
}
