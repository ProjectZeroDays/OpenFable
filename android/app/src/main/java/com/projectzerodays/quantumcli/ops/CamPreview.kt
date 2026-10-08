package com.projectzerodays.quantumcli.ops

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Camera live-frame engine — real JPEG stills from each discovered camera
 * via CamWar.snapshot (vendor + generic endpoints, basic/digest), decoded
 * for the camera list thumbnails and the tap-to-view preview dialog.
 *
 * This is honest stills, not RTSP decode: the preview dialog auto-refreshes
 * frames on a short interval so it reads like live video while staying a
 * pure-HTTP, no-extra-dependency implementation. A camera that answers
 * none of the snapshot endpoints shows a NO SIGNAL state, never a fake
 * image.
 */
object CamPreview {

    /** Cached thumbnails keyed by camera IP (small, inSampleSize-decoded). */
    private val thumbCache = ConcurrentHashMap<String, Bitmap>()

    /** Per-IP in-flight fetch guard so parallel card compositions share one hit. */
    private val inflight = ConcurrentHashMap.newKeySet<String>()

    const val THUMB_MAX_DIM = 320
    const val FRAME_MAX_DIM = 1280

    /** Live-frame result: the decoded bitmap + capture timestamp text. */
    data class Frame(val bitmap: Bitmap, val ts: String)

    /**
     * Thumbnail for a camera card — served from cache when present, fetched
     * (once per IP, guarded by [inflight]) otherwise. Returns null when the
     * camera answers none of the snapshot endpoints. Runs network + decode;
     * call from Dispatchers.IO.
     */
    fun thumbnail(ip: String, vendor: String, creds: String?): Bitmap? {
        thumbCache[ip]?.let { return it }
        if (!inflight.add(ip)) return null
        try {
            val bytes = CamWar.snapshot(ip, vendor, creds) ?: return null
            val bmp = decodeSampled(bytes, THUMB_MAX_DIM) ?: return null
            thumbCache[ip] = bmp
            return bmp
        } finally {
            inflight.remove(ip)
        }
    }

    /**
     * Fresh full frame for the preview dialog (never cached — the preview
     * wants the newest image). Null when the camera is unreachable.
     */
    fun frame(ip: String, vendor: String, creds: String?): Frame? {
        val bytes = CamWar.snapshot(ip, vendor, creds) ?: return null
        val bmp = decodeSampled(bytes, FRAME_MAX_DIM) ?: return null
        return Frame(bmp, nowTs())
    }

    /** Drop the cached thumbnail (e.g. after PTZ nudges moved the view). */
    fun invalidate(ip: String) {
        thumbCache.remove(ip)
    }

    fun nowTs(): String =
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    /**
     * Two-pass decode: bounds first, then decode with an inSampleSize that
     * brings the longest edge near [maxDim]. Null when bytes are not a
     * decodable image.
     */
    fun decodeSampled(bytes: ByteArray, maxDim: Int): Bitmap? {
        if (bytes.size < 4) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = calcInSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    /**
     * Power-of-two sample size so the decoded longest edge lands at or just
     * above [maxDim] — pure math, unit-tested.
     */
    fun calcInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        if (width <= 0 || height <= 0 || maxDim <= 0) return 1
        var sample = 1
        var longEdge = maxOf(width, height)
        while (longEdge / 2 >= maxDim) {
            sample *= 2
            longEdge /= 2
        }
        return sample
    }
}
