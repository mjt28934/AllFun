package eu.kanade.tachiyomi.network

import okhttp3.Response

/**
 * Exception that handles HTTP codes considered not successful by OkHttp.
 *
 * @see Response.isSuccessful
 * @since tachiyomix 1.6
 */
class HttpException(val code: Int) : IllegalStateException("HTTP error $code")
