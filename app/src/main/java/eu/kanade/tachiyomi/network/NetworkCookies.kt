package eu.kanade.tachiyomi.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * In-memory cookie jar shared by every extension client.
 *
 * Site sessions and Cloudflare clearance cookies live here for the life of the process,
 * which is enough for browsing and reading; nothing is persisted to disk.
 */
class MemoryCookieJar : CookieJar {

    private val cookies = ArrayList<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(this.cookies) {
            for (cookie in cookies) {
                this.cookies.removeAll {
                    it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path
                }
                this.cookies.add(cookie)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(cookies) {
            val now = System.currentTimeMillis()
            cookies.removeAll { it.expiresAt < now }
            return cookies.filter { it.matches(url) }
        }
    }

    /** Imports a `Cookie:` header string, as produced by [android.webkit.CookieManager]. */
    fun saveFromHeader(url: HttpUrl, header: String) {
        if (header.isBlank()) return
        val parsed = header.split(';').mapNotNull { part ->
            runCatching { Cookie.parse(url, part.trim()) }.getOrNull()
        }
        if (parsed.isNotEmpty()) saveFromResponse(url, parsed)
    }
}
