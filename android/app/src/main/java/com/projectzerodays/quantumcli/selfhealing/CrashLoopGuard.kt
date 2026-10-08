package com.projectzerodays.quantumcli.selfhealing

import java.io.File

/**
 * Crash-loop guard — failure class 8 of the healing daemon. Three
 * consecutive failed boots put the app into Safe Mode (core-only); a single
 * successful boot resets the counter. The strike count persists through the
 * injected [StrikeStore] so it survives the very crashes it counts.
 *
 * Integration note (honest): `SafeModeActivity` already exists in the app;
 * wiring this guard's verdict into the boot path is the pending PR (the
 * guard itself is fully implemented + unit-tested).
 */
class CrashLoopGuard(private val store: StrikeStore) {

    interface StrikeStore {
        fun strikes(): Int
        fun setStrikes(count: Int)
    }

    class InMemoryStrikeStore : StrikeStore {
        private var count = 0
        override fun strikes(): Int = count
        override fun setStrikes(count: Int) {
            this.count = count.coerceAtLeast(0)
        }
    }

    /** File-backed store (atomic replace) — survives process death. */
    class FileStrikeStore(private val file: File) : StrikeStore {
        override fun strikes(): Int = try {
            if (file.isFile) file.readText().trim().toIntOrNull() ?: 0 else 0
        } catch (_: Exception) {
            0
        }

        override fun setStrikes(count: Int) {
            try {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(count.toString())
                // Windows renameTo fails when the destination exists — remove first.
                try {
                    file.delete()
                } catch (_: Exception) {
                }
                if (!tmp.renameTo(file)) {
                    // Fall back to a direct write; never fail closed.
                    file.writeText(count.toString())
                    try {
                        tmp.delete()
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
                // best-effort — a lost strike count fails open (no safe mode),
                // never fails closed into a lockout
            }
        }
    }

    companion object {
        const val STRIKE_LIMIT = 3
    }

    /** Record a boot outcome; returns the current strike count. */
    fun recordBoot(bootSucceeded: Boolean): Int {
        val next = if (bootSucceeded) {
            0
        } else {
            (store.strikes() + 1).coerceAtMost(STRIKE_LIMIT)
        }
        store.setStrikes(next)
        return next
    }

    fun shouldEnterSafeMode(): Boolean = store.strikes() >= STRIKE_LIMIT

    fun reset() {
        store.setStrikes(0)
    }
}
