package app.allfun.data.update

/**
 * Semantic-version comparison for release tags such as `v1.0.0` or `1.2.3`.
 *
 * Only the numeric part is compared: a build suffix (`-beta.1`, `+12`) is ignored, and
 * missing components count as zero (`1.1` == `1.1.0`). That is enough to answer the only
 * question the updater asks — is the published release newer than the installed build?
 */
object AppVersion {

    /** `v1.0.0` -> `1.0.0`. */
    fun fromTag(tag: String): String = tag.trim().removePrefix("v").removePrefix("V")

    /** Numeric components of a version, padding to [size] entries. */
    fun components(version: String): List<Int> {
        val core = fromTag(version).substringBefore('-').substringBefore('+')
        val parts = core.split('.').map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull()
        }
        require(parts.isNotEmpty() && parts.all { it != null }) { "Not a version: $version" }
        return parts.map { it!! }
    }

    /** Negative when [a] < [b], zero when equal, positive when [a] > [b]. */
    fun compare(a: String, b: String): Int {
        val left = components(a)
        val right = components(b)
        val size = maxOf(left.size, right.size, 3)
        for (i in 0 until size) {
            val l = left.getOrElse(i) { 0 }
            val r = right.getOrElse(i) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    fun isNewer(candidate: String, installed: String): Boolean = compare(candidate, installed) > 0

    /**
     * `versionCode` derived from a semantic version: major * 10_000 + minor * 100 + patch.
     * Used by the release workflow so both values move together and an older build can
     * never install over a newer one.
     */
    fun toVersionCode(version: String): Int {
        val parts = components(version)
        val major = parts.getOrElse(0) { 0 }
        val minor = parts.getOrElse(1) { 0 }
        val patch = parts.getOrElse(2) { 0 }
        return major * 10_000 + minor * 100 + patch
    }
}
