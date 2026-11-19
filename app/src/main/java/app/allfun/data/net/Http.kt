package app.allfun.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The response was not JSON; [detail] keeps the raw evidence for diagnostics. */
class InvalidResponseException(
    message: String,
    val detail: String? = null
) : IOException(message)

class Http(
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException(
                        "HTTP ${response.code} for ${request.url}" +
                            body.takeIf { it.isNotBlank() }?.let { ": ${it.take(160)}" }.orEmpty()
                    )
                }
                body
            }
        }

    /**
     * Reads JSON, refusing to hand HTML (a website, a Cloudflare challenge, a login page)
     * to the JSON parser. Without this check a provider that moved or started answering
     * with a web page produced a parser error such as "Unexpected JSON token at offset 11",
     * which told the user nothing and looked like an AllFun bug.
     */
    suspend fun getJson(url: String, headers: Map<String, String> = emptyMap()): JsonElement =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val contentType = response.header("content-type").orEmpty()
                if (!response.isSuccessful) {
                    throw IOException(
                        "HTTP ${response.code} for ${request.url}" +
                            body.takeIf { it.isNotBlank() }?.let { ": ${it.take(160)}" }.orEmpty()
                    )
                }
                parseJsonBody(body, contentType, request.url.toString())
            }
        }

    /** Same validation as [getJson], used by callers that already have the response text. */
    fun parseJsonBody(body: String, contentType: String?, url: String): JsonElement {
        val type = contentType.orEmpty()
        val trimmed = body.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        if (trimmed.isEmpty()) {
            throw InvalidResponseException("$url returned an empty response", "empty body")
        }
        val looksHtml = trimmed.startsWith("<!doctype", ignoreCase = true) ||
            trimmed.startsWith("<html", ignoreCase = true) ||
            type.contains("text/html", ignoreCase = true)
        if (looksHtml) {
            throw InvalidResponseException(
                "$url returned a web page instead of JSON. This is usually a wrong add-on " +
                    "address, a sign-in or Cloudflare challenge page, or a server error.",
                trimmed.take(400)
            )
        }
        val looksJson = trimmed.startsWith("{") || trimmed.startsWith("[")
        if (!looksJson && type.isNotBlank() &&
            !type.contains("json", ignoreCase = true) &&
            !type.contains("text/plain", ignoreCase = true)
        ) {
            throw InvalidResponseException(
                "$url returned ${type.substringBefore(';')} instead of JSON",
                trimmed.take(400)
            )
        }
        return try {
            json.parseToJsonElement(body)
        } catch (error: Exception) {
            throw InvalidResponseException(
                "$url returned malformed JSON",
                "$error\n${trimmed.take(400)}"
            )
        }
    }

    /**
     * Downloads a binary payload with a hard size ceiling. Repository APKs and plugin
     * scripts are untrusted input; an accidental multi-gigabyte response must not fill
     * the device before validation runs.
     */
    suspend fun getBytes(
        url: String,
        headers: Map<String, String> = emptyMap(),
        maxBytes: Long = 64L * 1024 * 1024
    ): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for ${request.url}")
            }
            val body = response.body ?: throw IOException("Empty response")
            val declared = body.contentLength()
            if (declared > maxBytes) {
                throw IOException("The file is larger than the ${maxBytes / (1024 * 1024)} MB limit")
            }
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            body.byteStream().use { input ->
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) {
                        throw IOException("The file is larger than the ${maxBytes / (1024 * 1024)} MB limit")
                    }
                    output.write(buffer, 0, read)
                }
            }
            output.toByteArray()
        }
    }

    suspend fun postJson(
        url: String,
        payload: String,
        headers: Map<String, String> = emptyMap()
    ): JsonElement = withContext(Dispatchers.IO) {
        val body = payload.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${text.take(200)}")
            }
            if (text.isBlank()) JsonObject(emptyMap())
            else parseJsonBody(text, response.header("content-type"), request.url.toString())
        }
    }

    /**
     * Raw request used by the LNReader plugin host. Unlike [get]/[postJson] this keeps
     * error bodies and status codes, because plugins inspect them (403 captchas, 404
     * "chapter removed", and so on).
     */
    suspend fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        binary: Boolean = false
    ): RawResponse = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> builder.header(k, v) }
        val requestBody = body?.toRequestBody(
            headers.entries.firstOrNull { it.key.equals("content-type", true) }
                ?.value?.toMediaType()
                ?: "text/plain; charset=utf-8".toMediaType()
        )
        when (method.uppercase()) {
            "POST" -> builder.post(requestBody ?: ByteArray(0).toRequestBody())
            "PUT" -> builder.put(requestBody ?: ByteArray(0).toRequestBody())
            "PATCH" -> builder.patch(requestBody ?: ByteArray(0).toRequestBody())
            "DELETE" -> builder.delete()
            else -> builder.get()
        }
        client.newCall(builder.build()).execute().use { response ->
            val bytes = if (binary) response.body?.bytes() else null
            RawResponse(
                status = response.code,
                statusText = response.message,
                url = response.request.url.toString(),
                headers = response.headers.toMap(),
                body = if (binary) "" else response.body?.string().orEmpty(),
                bytes = bytes
            )
        }
    }
}

data class RawResponse(
    val status: Int,
    val statusText: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String,
    val bytes: ByteArray? = null
)

fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

fun JsonObject.strings(key: String): List<String> =
    arr(key)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
