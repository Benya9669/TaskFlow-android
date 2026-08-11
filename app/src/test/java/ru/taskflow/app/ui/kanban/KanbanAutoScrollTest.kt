package ru.taskflow.app.ui.kanban

import org.junit.Assert.assertEquals
import org.junit.Test

class KanbanAutoScrollTest {
    @Test
    fun scrollsTowardAvailableEdgeOnly() {
        assertEquals(-1, kanbanAutoScrollDirection(120f, 100f, 1100f, 72f, canScrollBackward = true, canScrollForward = true))
        assertEquals(1, kanbanAutoScrollDirection(1080f, 100f, 1100f, 72f, canScrollBackward = true, canScrollForward = true))
        assertEquals(0, kanbanAutoScrollDirection(600f, 100f, 1100f, 72f, canScrollBackward = true, canScrollForward = true))
        assertEquals(0, kanbanAutoScrollDirection(120f, 100f, 1100f, 72f, canScrollBackward = false, canScrollForward = true))
        assertEquals(0, kanbanAutoScrollDirection(1080f, 100f, 1100f, 72f, canScrollBackward = true, canScrollForward = false))
    }
}
