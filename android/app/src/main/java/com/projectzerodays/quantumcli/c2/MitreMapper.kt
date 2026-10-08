package com.projectzerodays.quantumcli.c2

/**
 * MitreMapper parity — keyword rules over the audit log. Every rule mirrors
 * the Python MitreMapper.RULES table exactly (keyword -> ATT&CK technique).
 */
object MitreMapper {

    val RULES: List<Pair<String, String>> = listOf(
        "cam war" to "T1595.002", "camwar" to "T1595.002",
        "log4shell" to "T1190", "citrix" to "T1190", "pan-os" to "T1190",
        "panos" to "T1190", "confluence" to "T1190", "weblogic" to "T1190",
        "dirtypipe" to "T1068", "dirty pipe" to "T1068", "pwnkit" to "T1068",
        "baron samedit" to "T1068", "samedit" to "T1068", "nf_tables" to "T1068",
        "flashfill" to "T1499", "emojism" to "T1132.001", "emoji" to "T1132.001",
        "rtsp" to "T1110.003", "brute" to "T1110.003", "zerocheck" to "T1595",
        "mshtml" to "T1203", "boothole" to "T1542.001", "beacon" to "T1071",
        "c2" to "T1071", "killswitch" to "T1485", "wipe" to "T1485",
        "keylogger" to "T1056.001", "persistx" to "T1547",
        "registry" to "T1112", "snapshot" to "T1560", "seal" to "T1560",
        "ghostdns" to "T1071.004", "doh" to "T1071.004",
        "worm" to "T1021", "phish" to "T1566", "harvest" to "T1005",
    )

    /** coverage over the given audit events (sorted technique IDs). */
    fun coverage(events: List<AuditEvent>): List<String> {
        val hits = LinkedHashMap<String, Int>()
        for (ev in events) {
            val hay = (ev.ts + " " + ev.tag + " " + ev.msg).lowercase()
            for ((kw, tid) in RULES) {
                if (hay.contains(kw)) {
                    hits[tid] = (hits[tid] ?: 0) + 1
                }
            }
        }
        return hits.keys.sorted()
    }

    /** map_audit parity: {generated, coverage, counts, events_scanned}. */
    fun mapAudit(events: List<AuditEvent>): org.json.JSONObject {
        val counts = org.json.JSONObject()
        for (ev in events) {
            val hay = (ev.ts + " " + ev.tag + " " + ev.msg).lowercase()
            for ((kw, tid) in RULES) {
                if (hay.contains(kw)) {
                    counts.put(tid, counts.optInt(tid) + 1)
                }
            }
        }
        val coverage = ArrayList<String>()
        counts.keys().forEach { coverage.add(it) }
        coverage.sort()
        return org.json.JSONObject()
            .put(
                "generated",
                java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss",
                    java.util.Locale.US
                ).format(java.util.Date())
            )
            .put("coverage", org.json.JSONArray(coverage))
            .put("counts", counts)
            .put("events_scanned", events.size)
    }
}
