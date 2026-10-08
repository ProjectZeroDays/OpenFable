package com.projectzerodays.quantumcli.widgetengine.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.projectzerodays.quantumcli.data.QuantSettings

/**
 * App-wide clipboard hygiene.
 *
 * Every copy armed through [copyAndScheduleClear] (or [armClear]) wipes the
 * clipboard after the operator's configured TTL — secrets don't linger
 * where other apps can read them. Re-arming cancels the previous clear.
 */
object ClipboardGuard {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingClear: Runnable? = null
    private var lastCopied: String? = null

    fun ttlSeconds(): Int = QuantSettings.state.value.clipboardClearSec.coerceAtLeast(5)

    /** Copy [value] now and schedule the wipe. Returns the TTL applied. */
    fun copyAndScheduleClear(context: Context, value: String): Int {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("quantum", value))
        lastCopied = value
        return armClear(context)
    }

    /** Copy from a composable context (no Context plumbing at call sites). */
    fun copyAndScheduleClear(value: String): Int {
        val ctx = com.projectzerodays.quantumcli.QuantumApp.ctx
        return copyAndScheduleClear(ctx, value)
    }

    /** Schedule a wipe of the current clipboard after the TTL. */
    fun armClear(context: Context): Int {
        cancel()
        val seconds = ttlSeconds()
        val r = Runnable {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            // Only clear what WE copied — never wipe an operator's own paste.
            if (lastCopied != null && cm.hasPrimaryClip() &&
                cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() == lastCopied
            ) {
                cm.setPrimaryClip(ClipData.newPlainText("", ""))
            }
            lastCopied = null
        }
        pendingClear = r
        handler.postDelayed(r, seconds * 1000L)
        return seconds
    }

    fun cancel() {
        pendingClear?.let { handler.removeCallbacks(it) }
        pendingClear = null
    }
}
