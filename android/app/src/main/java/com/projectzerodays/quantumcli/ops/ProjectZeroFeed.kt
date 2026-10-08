package com.projectzerodays.quantumcli.ops

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/**
 * Project Zero feed card — reads the Google Project Zero Atom feed
 * (https://googleprojectzero.blogspot.com/feeds/posts/default) via Net.httpGet
 * and returns the latest N entries (title, link, published). Parses with
 * XmlPullParser; a regex fallback covers malformed feeds.
 */
object ProjectZeroFeed {

    const val FEED_URL = "https://googleprojectzero.blogspot.com/feeds/posts/default"

    data class Entry(val title: String, val link: String, val published: String)

    /**
     * Latest [limit] entries; throws on network/parse failure so the caller
     * can surface a friendly status line.
     */
    fun latest(limit: Int = 3): List<Entry> {
        val r = Net.httpGet(FEED_URL, timeoutMs = 10_000)
        if (r.error != null) throw IllegalStateException(r.error)
        if (!r.ok) throw IllegalStateException("HTTP ${r.status}")
        val xml = r.text()
        return try {
            parseAtom(xml, limit)
        } catch (e: Exception) {
            parseRegex(xml, limit)
        }
    }

    // --------------------------------------------------------------- atom pull
    private fun parseAtom(xml: String, limit: Int): List<Entry> {
        val p = Xml.newPullParser()
        p.setInput(StringReader(xml))
        val out = ArrayList<Entry>(limit)
        var inEntry = false
        var title = ""
        var link = ""
        var published = ""
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT && out.size < limit) {
            when (event) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "entry" -> {
                        inEntry = true
                        title = ""
                        link = ""
                        published = ""
                    }
                    "title" -> if (inEntry) title = p.nextText()
                    "published" -> if (inEntry) published = p.nextText()
                    "link" -> if (inEntry && link.isBlank()) {
                        for (i in 0 until p.attributeCount) {
                            if (p.getAttributeName(i) == "href") {
                                link = p.getAttributeValue(i)
                                break
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> if (p.name == "entry" && inEntry) {
                    out.add(Entry(title.trim(), link.trim(), published.trim()))
                    inEntry = false
                }
            }
            event = p.next()
        }
        if (out.isEmpty()) throw IllegalStateException("no <entry> parsed")
        return out
    }

    // ------------------------------------------------------------- regex safety
    private fun parseRegex(xml: String, limit: Int): List<Entry> {
        val entries = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .map { it.groupValues[1] }
            .take(limit)
            .map { block ->
                Entry(
                    title = group(block, "title") ?: "(untitled)",
                    link = group(block, "link") ?: FEED_URL,
                    published = group(block, "published") ?: "",
                )
            }
            .toList()
        if (entries.isEmpty()) throw IllegalStateException("feed unparsable")
        return entries
    }

    private fun group(block: String, tag: String): String? {
        val m = Regex("<$tag[^>]*>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL).find(block)
            ?: return null
        val raw = m.groupValues[1]
        // link elements are self-closing with the href on the tag itself
        if (raw.isBlank()) {
            val href = Regex("<$tag[^>]*href=\"([^\"]+)\"").find(block) ?: return null
            return href.groupValues[1]
        }
        return raw.trim()
    }
}
