package app.allfun.data.source

import app.allfun.data.net.arr
import app.allfun.data.net.str
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Reads the `resources` declaration from a Stremio addon manifest.
 *
 * The Stremio protocol lists resources such as `catalog`, `meta` and `stream` as either
 * bare strings or objects with `types`/`idPrefixes`, and an addon often exposes only some
 * of them: a catalogue-only aggregator answers `catalog` and returns an HTML error page
 * for `meta`. Every lookup therefore has to check the declaration before making a
 * request instead of assuming that an installed addon can serve every resource.
 */
internal object StremioManifest {

    /** One declared resource. Empty [types]/[idPrefixes] mean "no restriction". */
    data class Resource(val types: Set<String>, val idPrefixes: List<String>)

    fun resource(manifest: JsonObject?, name: String): Resource? =
        manifest?.arr("resources")?.firstNotNullOfOrNull { element ->
            when (element) {
                is JsonPrimitive -> element.contentOrNull
                    ?.takeIf { it == name }
                    ?.let { Resource(emptySet(), emptyList()) }
                is JsonObject -> if (element.str("name") == name) {
                    Resource(
                        types = element.arr("types")
                            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                            ?.toSet()
                            .orEmpty(),
                        idPrefixes = element.arr("idPrefixes")
                            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                            .orEmpty()
                    )
                } else {
                    null
                }
                else -> null
            }
        }

    /**
     * Legacy rows predate stored manifests, and their addon was installed and used before,
     * so they keep the old "assume it answers" behaviour.
     */
    fun supports(manifest: JsonObject?, name: String): Boolean =
        manifest == null || resource(manifest, name) != null

    fun accepts(manifest: JsonObject?, name: String, type: String, id: String): Boolean {
        if (manifest == null) return true
        val spec = resource(manifest, name) ?: return false
        val typeOk = spec.types.isEmpty() || type in spec.types
        val idOk = spec.idPrefixes.isEmpty() || spec.idPrefixes.any { id.startsWith(it) }
        return typeOk && idOk
    }
}
