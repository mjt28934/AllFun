package app.allfun.data.epub

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

data class EpubChapter(val id: String, val title: String, val href: String)

data class EpubBook(
    val title: String,
    val author: String?,
    val coverFile: File?,
    val chapters: List<EpubChapter>
)

/**
 * Minimal, dependency-free EPUB 2/3 reader: unpacks the container, reads the OPF spine and
 * pulls chapter titles out of the navigation document when one exists.
 */
object EpubParser {

    fun extract(epubFile: File, targetDir: File): EpubBook {
        targetDir.deleteRecursively()
        targetDir.mkdirs()

        ZipFile(epubFile).use { zip ->
            for (entry in zip.entries()) {
                if (entry.isDirectory) continue
                val name = entry.name.replace('\\', '/')
                if (name.contains("..")) continue
                val out = File(targetDir, name)
                val root = targetDir.canonicalPath + File.separator
                if (!out.canonicalPath.startsWith(root)) continue
                out.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }

        val container = File(targetDir, "META-INF/container.xml")
        if (!container.exists()) error("Not an EPUB: META-INF/container.xml is missing")

        val containerDoc = parseXml(container)
        val rootFile = containerDoc.getElementsByTagName("rootfile").item(0) as? Element
            ?: error("Not an EPUB: no rootfile in container.xml")
        val opfRel = rootFile.getAttribute("full-path")
        val opf = File(targetDir, opfRel)
        if (!opf.exists()) error("Not an EPUB: package document $opfRel is missing")

        val opfDir = opf.parentFile
            ?.relativeTo(targetDir)
            ?.path
            ?.replace('\\', '/')
            .orEmpty()
        val doc = parseXml(opf)

        val metadata = doc.getElementsByTagName("metadata").item(0) as? Element
        val title = metadata?.firstText("title")?.takeIf { it.isNotBlank() }
            ?: opf.nameWithoutExtension
        val author = metadata?.firstText("creator")?.takeIf { it.isNotBlank() }

        // manifest: id -> href / properties / media type
        data class Item(val href: String, val mediaType: String, val properties: String)
        val manifest = mutableMapOf<String, Item>()
        val items = doc.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val item = items.item(i) as? Element ?: continue
            val id = item.getAttribute("id") ?: continue
            val href = item.getAttribute("href") ?: continue
            manifest[id] = Item(
                href = resolve(opfDir, href),
                mediaType = item.getAttribute("media-type").orEmpty(),
                properties = item.getAttribute("properties").orEmpty()
            )
        }

        // spine order
        val spine = mutableListOf<String>()
        val itemRefs = doc.getElementsByTagName("itemref")
        for (i in 0 until itemRefs.length) {
            val ref = itemRefs.item(i) as? Element ?: continue
            val idref = ref.getAttribute("idref")
            manifest[idref]?.let { spine.add(it.href) }
        }
        if (spine.isEmpty()) {
            manifest.values
                .filter { it.mediaType.contains("xhtml") || it.href.endsWith(".html") }
                .forEach { spine.add(it.href) }
        }

        // chapter titles from EPUB3 nav, EPUB2 ncx, or the spine order
        val toc = mutableMapOf<String, String>()
        val navItem = manifest.values.firstOrNull { it.properties.split(' ').contains("nav") }
        if (navItem != null) {
            val navFile = File(targetDir, navItem.href)
            if (navFile.exists()) {
                toc += parseNavHrefTitles(navFile.readText(), navDir(navItem.href))
            }
        }
        if (toc.isEmpty()) {
            val ncxId = doc.documentElement.getAttribute("toc")
            val ncx = manifest[ncxId] ?: manifest.values.firstOrNull { it.mediaType.contains("dtbncx") }
            if (ncx != null) {
                val ncxFile = File(targetDir, ncx.href)
                if (ncxFile.exists()) {
                    toc += parseNcxTitles(ncxFile.readText(), navDir(ncx.href))
                }
            }
        }

        val chapters = spine.distinct().mapIndexed { index, href ->
            EpubChapter(
                id = href,
                title = toc[href] ?: "Chapter ${index + 1}",
                href = href
            )
        }
        if (chapters.isEmpty()) error("Not an EPUB: no readable chapters")

        // cover
        var coverHref: String? = null
        manifest.values.firstOrNull { it.properties.split(' ').contains("cover-image") }
            ?.let { coverHref = it.href }
        if (coverHref == null) {
            val metas = metadata?.getElementsByTagName("meta")
            if (metas != null) {
                for (i in 0 until metas.length) {
                    val meta = metas.item(i) as? Element ?: continue
                    if (meta.getAttribute("name") == "cover") {
                        coverHref = manifest[meta.getAttribute("content")]?.href
                    }
                }
            }
        }
        val coverFile = coverHref?.let { File(targetDir, it).takeIf(File::exists) }

        return EpubBook(title = title, author = author, coverFile = coverFile, chapters = chapters)
    }

    private fun parseXml(file: File): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isExpandEntityReferences = false
        runCatching {
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        }
        runCatching {
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        return factory.newDocumentBuilder().parse(file)
    }

    private fun Element.firstText(tag: String): String? {
        // EPUB metadata is namespaced (`dc:title`), and with namespace awareness off the
        // tag name keeps its prefix, so match on the local name instead.
        val nodes = getElementsByTagName("*")
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as? Element ?: continue
            val localName = element.localName ?: element.tagName.substringAfter(':')
            if (localName.equals(tag, ignoreCase = true)) return element.textContent?.trim()
        }
        return null
    }

    private fun navDir(href: String): String =
        if (href.contains('/')) href.substringBeforeLast('/') else ""

    private val anchorRegex = Regex(
        """<a[^>]*href\s*=\s*"([^"#]+)[^"]*"[^>]*>(.*?)</a>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private fun parseNavHrefTitles(html: String, baseDir: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        for (match in anchorRegex.findAll(html)) {
            val label = stripTags(match.groupValues[2])
            if (label.isBlank()) continue
            val href = resolve(baseDir, decodeUrl(match.groupValues[1]))
            out.putIfAbsent(href, label)
        }
        return out
    }

    private val ncxRegex = Regex(
        """<navLabel[^>]*>\s*<text[^>]*>(.*?)</text>.*?<content[^>]*src\s*=\s*"([^"#]+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private fun parseNcxTitles(ncx: String, baseDir: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        for (match in ncxRegex.findAll(ncx)) {
            val label = stripTags(match.groupValues[1])
            if (label.isBlank()) continue
            val href = resolve(baseDir, decodeUrl(match.groupValues[2]))
            out.putIfAbsent(href, label)
        }
        return out
    }

    private fun stripTags(value: String): String =
        value.replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun decodeUrl(href: String): String =
        runCatching { URLDecoder.decode(href, "UTF-8") }.getOrDefault(href)

    /** Resolves an href relative to a directory inside the book, normalising ./ and ../. */
    fun resolve(baseDir: String, href: String): String {
        val clean = href.substringBefore('#')
        if (clean.startsWith("http://") || clean.startsWith("https://")) return clean
        val start = if (baseDir.isBlank()) emptyList() else baseDir.split('/')
        val parts = start + clean.split('/')
        val out = ArrayDeque<String>()
        for (part in parts) {
            when (part) {
                "", "." -> Unit
                ".." -> out.removeLastOrNull()
                else -> out.addLast(part)
            }
        }
        return out.joinToString("/")
    }
}
