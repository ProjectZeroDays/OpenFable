package com.projectzerodays.quantumcli.ops

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CamPreview.decodeSampled needs android.graphics, which JVM tests cannot
 * run — but the inSampleSize math is pure and fully covered here.
 *
 * Contract: calcInSampleSize returns the smallest power-of-two sample whose
 * decoded long edge lands in [maxDim, 2*maxDim) — halving stops as soon
 * as it would undershoot maxDim.
 */
class CamPreviewTest {

    @Test
    fun `sample size is 1 when halving would undershoot`() {
        // 320x240 already fits 320
        assertEquals(1, CamPreview.calcInSampleSize(320, 240, 320))
        // 1920 halved is 960 < 1280 — keep full size (lands just above cap)
        assertEquals(1, CamPreview.calcInSampleSize(1920, 1080, 1280))
        // 640x480 into a 640 tile
        assertEquals(1, CamPreview.calcInSampleSize(640, 480, 640))
    }

    @Test
    fun `sample size halves the long edge down to the cap`() {
        // 640 -> 320 == cap
        assertEquals(2, CamPreview.calcInSampleSize(640, 480, 320))
        // 1280 -> 640 -> 320
        assertEquals(4, CamPreview.calcInSampleSize(1280, 720, 320))
        // 2560 -> 1280 -> 640 -> 320
        assertEquals(8, CamPreview.calcInSampleSize(2560, 1440, 320))
        // portrait orientation counts the same via the long edge
        assertEquals(8, CamPreview.calcInSampleSize(1440, 2560, 320))
    }

    @Test
    fun `sample size is power of two and never undershoots`() {
        // 1920 -> 960 (>=320) -> 480 (>=320) -> 240 (<320 stop) => 4, decoded 480
        assertEquals(4, CamPreview.calcInSampleSize(1920, 1080, 320))
        assertEquals(8, CamPreview.calcInSampleSize(3840, 2160, 320))
        // decoded long edge of every power-of-two input stays >= maxDim
        for (w in listOf(640, 1280, 2560, 5120)) {
            val s = CamPreview.calcInSampleSize(w, 720, 320)
            assertTrue(w / s >= 320)
            assertTrue(w / s < 640)
        }
    }

    @Test
    fun `degenerate inputs never divide by zero`() {
        assertEquals(1, CamPreview.calcInSampleSize(0, 0, 320))
        assertEquals(1, CamPreview.calcInSampleSize(100, 100, 0))
        assertEquals(1, CamPreview.calcInSampleSize(-5, 100, 320))
    }
}

private fun assertTrue(b: Boolean) = org.junit.Assert.assertTrue(b)
