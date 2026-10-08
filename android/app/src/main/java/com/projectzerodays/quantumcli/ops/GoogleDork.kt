package com.projectzerodays.quantumcli.ops

import java.net.URLEncoder

/**
 * Google dorking catalog — mirrors quantum.recon.googledork (same ids,
 * queries, operators, methods) for the offline-first APK. Builds search
 * URLs only; results render in the in-app WebView, nothing is scraped.
 *
 * The analyst methods are generic public OSINT methodology — no agency
 * attribution exists anywhere in this file.
 */
object GoogleDork {

    data class Operator(val op: String, val desc: String, val example: String)

    val OPERATORS: List<Operator> = listOf(
        Operator("site:", "Restrict results to one domain or TLD.", "site:example.com filetype:pdf"),
        Operator("filetype:", "Only files of this extension (alias: ext:).", "filetype:xls password"),
        Operator("intitle:", "Term must appear in the page title.", "intitle:\"index of\""),
        Operator("inurl:", "Term must appear in the URL.", "inurl:admin.php"),
        Operator("intext:", "Term must appear in the page body.", "intext:password"),
        Operator("cache:", "Show Google's cached copy of a URL.", "cache:example.com/page"),
        Operator("related:", "Pages Google considers similar.", "related:example.com"),
        Operator("before:/after:", "Date-range filter (YYYY-MM-DD).", "breach after:2023-01-01"),
        Operator("\"\"", "Exact-phrase anchor — kills stemming/synonyms.", "\"db_password =\""),
        Operator("-", "Exclude a term (no space after the minus).", "router -site:vendor.com"),
        Operator("OR / |", "Either term may match (uppercase OR).", "inurl:login OR inurl:signin"),
        Operator("*", "Wildcard for whole words inside a phrase.", "\"password * changed\""),
    )

    enum class Engine(val label: String, val template: String) {
        GOOGLE("Google", "https://www.google.com/search?q="),
        BING("Bing", "https://www.bing.com/search?q="),
        DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
        STARTPAGE("Startpage", "https://www.startpage.com/sp/search?query="),
    }

    data class Preset(
        val id: String,
        val title: String,
        val category: String,
        val query: String,
        val note: String,
    )

    val CATEGORIES: List<String> = listOf(
        "exposed-files", "login-portals", "cameras", "directories",
        "errors", "devices", "documents",
    )

    val PRESETS: List<Preset> = listOf(
        Preset("env-exposed", "Exposed environment files", "exposed-files", "filename:.env \"DB_PASSWORD\"", "Config files indexed with credentials. Verify ownership before touching."),
        Preset("sql-dumps", "SQL database dumps", "exposed-files", "filetype:sql \"INSERT INTO\" password", "Dump files left on web roots."),
        Preset("key-files", "Private key material", "exposed-files", "filetype:pem \"PRIVATE KEY\"", "PEM/PPK keys. Report, do not use."),
        Preset("config-bak", "Backup / old config copies", "exposed-files", "filetype:bak inurl:conf", "Editor and admin backup copies."),
        Preset("git-exposed", "Exposed .git directories", "exposed-files", "inurl:.git/HEAD \"ref:\"", "Full repo history is often reconstructible."),
        Preset("login-generic", "Login pages", "login-portals", "inurl:login OR inurl:signin intitle:login", "Baseline portal discovery; scope with site:."),
        Preset("admin-panels", "Admin panels", "login-portals", "intitle:\"admin panel\" OR intitle:\"control panel\"", "Admin interfaces. Default-creds testing needs authorization."),
        Preset("wp-login", "WordPress logins", "login-portals", "inurl:wp-login.php", "Pair with version dorks for targeting notes."),
        Preset("ip-cameras", "Network cameras (generic viewers)", "cameras", "inurl:view/view.shtml", "Unauthenticated camera viewers. Viewing private feeds needs authorization."),
        Preset("webcam-xp", "webcamXP-style pages", "cameras", "intitle:\"webcamXP\"", "Classic exposed-cam pattern."),
        Preset("index-of", "Open directory listings", "directories", "intitle:\"index of\"", "The starting point for manual browsing; scope by target."),
        Preset("index-of-backup", "Directory listings with archives", "directories", "intitle:\"index of\" (zip OR tar OR bak)", "Listings that already advertise interesting files."),
        Preset("ftp-listings", "FTP directory indexes", "directories", "intitle:\"FTP root\" OR intitle:\"FTP directory\"", "Anonymous FTP roots indexed by crawlers."),
        Preset("sql-errors", "SQL error messages", "errors", "intext:\"SQL syntax\" OR intext:\"mysql_fetch\"", "Verbose errors that confirm injectable endpoints."),
        Preset("php-errors", "PHP warnings / stack traces", "errors", "intext:\"Fatal error\" intext:\".php\" \"on line\"", "Path disclosure + version hints."),
        Preset("router-pages", "Router / gateway admin", "devices", "intitle:\"router\" intitle:\"login\" -site:vendor.com", "Exclude vendor docs with the minus operator."),
        Preset("printer-panels", "Printer / MFP panels", "devices", "intitle:\"printer\" inurl:status", "Device panels; often no auth at all."),
        Preset("iot-dashboards", "IoT dashboards", "devices", "intitle:dashboard inurl:status temperature", "Sensor dashboards left open."),
        Preset("confidential-docs", "Confidential documents", "documents", "filetype:pdf confidential OR \"internal use only\"", "Leaked paperwork. Handle per engagement rules."),
        Preset("spreadsheets", "Public spreadsheets with secrets", "documents", "filetype:xls OR filetype:xlsx password", "Credential tables in indexed sheets."),
        Preset("slides-internal", "Internal slide decks", "documents", "filetype:ppt OR filetype:pptx \"internal only\"", "Roadmaps, creds in screenshots, architecture."),
        Preset("paste-dumps", "Paste-site credential dumps", "exposed-files", "site:pastebin.com password", "Combo lists and configs mirrored to pastes."),
        Preset("cisco-configs", "Network device configs", "devices", "filetype:cfg \"hostname\" \"enable password\"", "Switch/router configs with hashes."),
        Preset("jenkins-open", "Open CI consoles", "login-portals", "intitle:\"Dashboard [Jenkins]\"", "Unauthenticated build consoles = RCE-shaped."),
    )

    data class Method(val id: String, val title: String, val body: String, val steps: List<String>)

    val METHODS: List<Method> = listOf(
        Method(
            "scope-first", "Scope before you search",
            "Start every engagement query with site:target.tld (or a quoted unique string from the target, like a product codename). An unscoped dork returns the whole internet; a scoped one returns your target. Widen only after the scoped pass is exhausted.",
            listOf(
                "Run the preset with site:target.tld appended.",
                "If zero hits, drop one operator and re-run.",
                "Only then remove site: to see the global pattern.",
            ),
        ),
        Method(
            "chain-operators", "Chain operators to cut noise",
            "Each added operator is a filter. The professional pattern is broad → narrow: begin with one operator, inspect 2 pages of hits, then add the operator that removes the dominant false positive class (usually -term or a second inurl:/intitle:).",
            listOf(
                "Baseline: one operator, note the junk pattern.",
                "Add the exclusion or second anchor that kills the junk.",
                "Freeze the final query in your notes with the hit count.",
            ),
        ),
        Method(
            "anchor-phrases", "Anchor on exact phrases",
            "Quoted strings defeat stemming and synonym expansion. Error messages, banner strings, and default titles are the highest-signal anchors because only real deployments emit them verbatim.",
            listOf(
                "Harvest 2–3 verbatim strings from a known-good hit.",
                "Re-query each string quoted, scoped to the target.",
                "Pivot: quoted string + filetype: for the artifact class.",
            ),
        ),
        Method(
            "date-boxing", "Date-box to find fresh exposure",
            "after:/before: (YYYY-MM-DD) split a dork into 'always existed' vs 'newly indexed'. New exposures are the ones worth immediate triage — re-run high-value dorks monthly with after:<last run>.",
            listOf(
                "Run the dork unbounded, note total hits.",
                "Re-run with after:<your last review date>.",
                "Triage only the delta; log the date for next time.",
            ),
        ),
        Method(
            "filetype-pivot", "Pivot across filetypes",
            "One finding implies siblings: a leaked .pdf suggests .xls/.ppt/.bak in the same directory tree. Re-run the same anchor with each filetype: in turn, then intitle:\"index of\" on the parent path.",
            listOf(
                "Take the directory path from the first hit.",
                "Repeat the anchor per filetype.",
                "Close with an index-of query on the parent directory.",
            ),
        ),
        Method(
            "triage-discipline", "Triage like an analyst, not a collector",
            "Log every query, hit count, and verdict (true/false positive, authorized finding, out of scope). A dork list without a log is re-run forever; a logged one compounds. Never interact beyond read-only retrieval without written authorization.",
            listOf(
                "Record query + engine + date + hits + verdict.",
                "Mark false-positive patterns to auto-exclude next run.",
                "Escalate true findings through the engagement channel, not DMs.",
            ),
        ),
    )

    /** Search URL for [query] on [engine] (UTF-8 encoded). */
    fun buildSearchUrl(query: String, engine: Engine = Engine.GOOGLE): String =
        engine.template + URLEncoder.encode(query, "UTF-8")

    fun presetsByCategory(): Map<String, List<Preset>> {
        val grouped = LinkedHashMap<String, MutableList<Preset>>()
        CATEGORIES.forEach { grouped[it] = mutableListOf() }
        PRESETS.forEach { grouped.getOrPut(it.category) { mutableListOf() }.add(it) }
        return grouped
    }

    fun getPreset(id: String): Preset? = PRESETS.firstOrNull { it.id == id }
}
