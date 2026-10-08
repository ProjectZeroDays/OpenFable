package com.projectzerodays.quantumcli.widgetengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Grid reorder math — the single mutation a drag performs. */
class WidgetLayoutTest {

    private val layout = WidgetLayout("dashboard", listOf("a", "b", "c", "d"))

    @Test
    fun `moved relocates a widget forward`() {
        assertEquals(listOf("a", "c", "d", "b"), layout.moved(1, 3).orderedIds)
    }

    @Test
    fun `moved relocates a widget backward`() {
        // remove-then-insert at the target's current index
        assertEquals(listOf("b", "c", "a", "d"), layout.moved(0, 2).orderedIds)
    }

    @Test
    fun `out-of-range indices are a no-op`() {
        assertEquals(layout, layout.moved(-1, 0))
        assertEquals(layout, layout.moved(0, 9))
    }

    @Test
    fun `moveTo lands id on target position`() {
        assertEquals(listOf("b", "c", "a", "d"), layout.moveTo("a", "c").orderedIds)
        assertEquals(layout, layout.moveTo("a", "a"))
        assertEquals(layout, layout.moveTo("x", "b"))
    }

    @Test
    fun `immutability — original layout untouched`() {
        layout.moved(0, 3)
        assertNotEquals(listOf("b", "c", "d", "a"), layout.orderedIds)
        assertEquals(listOf("a", "b", "c", "d"), layout.orderedIds)
    }
}
