package com.projectzerodays.quantumcli.ops

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDorkTest {

    @Test
    fun catalogIsCompleteAndValid() {
        val ids = GoogleDork.PRESETS.map { it.id }
        assertEquals(ids.distinct(), ids)
        assertTrue(ids.size >= 20)
        GoogleDork.PRESETS.forEach {
            assertTrue(it.query.isNotBlank())
            assertTrue(it.title.isNotBlank())
            assertTrue(it.category in GoogleDork.CATEGORIES)
        }
        GoogleDork.CATEGORIES.forEach { cat ->
            assertTrue(
                "$cat has presets",
                GoogleDork.PRESETS.any { it.category == cat },
            )
        }
    }

    @Test
    fun kotlinCatalogMirrorsPythonIds() {
        // same preset ids as quantum.recon.googledork (offline-first mirror)
        for (id in listOf("env-exposed", "sql-dumps", "key-files", "login-generic",
                "ip-cameras", "index-of", "sql-errors", "jenkins-open")) {
            assertNotNull("missing preset $id", GoogleDork.getPreset(id))
        }
        assertEquals(
            "filetype:sql \"INSERT INTO\" password",
            GoogleDork.getPreset("sql-dumps")!!.query,
        )
    }

    @Test
    fun searchUrlsAreEncodedPerEngine() {
        val url = GoogleDork.buildSearchUrl("intitle:\"index of\"")
        assertTrue(url.startsWith("https://www.google.com/search?q="))
        assertTrue(" " !in url)
        assertTrue("%22" in url) // quotes encoded
        assertTrue(
            GoogleDork.buildSearchUrl("x", GoogleDork.Engine.BING)
                .startsWith("https://www.bing.com/search?q=x"),
        )
        assertTrue(
            "duckduckgo.com" in GoogleDork.buildSearchUrl("x", GoogleDork.Engine.DUCKDUCKGO),
        )
        assertTrue(
            "startpage.com" in GoogleDork.buildSearchUrl("x", GoogleDork.Engine.STARTPAGE),
        )
    }

    @Test
    fun operatorsAndMethodsAreDocumented() {
        assertTrue(GoogleDork.OPERATORS.size >= 10)
        GoogleDork.OPERATORS.forEach {
            assertTrue(it.desc.isNotBlank())
            assertTrue(it.example.isNotBlank())
        }
        assertTrue(GoogleDork.METHODS.size >= 5)
        val banned = listOf("cia", "nsa", "classified", "top secret")
        GoogleDork.METHODS.forEach { m ->
            assertTrue(m.body.isNotBlank())
            assertTrue(m.steps.size >= 2)
            val text = (m.title + " " + m.body + " " + m.steps.joinToString(" ")).lowercase()
            banned.forEach { word ->
                assertTrue("method ${m.id} mentions $word", word !in text)
            }
        }
    }
}
