package app.allfun.data

import java.util.UUID

/** Stable identifiers so a source result maps to the same row every time. */
object Ids {
    fun media(sourceId: String, key: String): String =
        UUID.nameUUIDFromBytes("$sourceId|$key".toByteArray()).toString()

    fun episode(mediaId: String, key: String): String =
        UUID.nameUUIDFromBytes("$mediaId|$key".toByteArray()).toString()

    fun fresh(): String = UUID.randomUUID().toString()
}
