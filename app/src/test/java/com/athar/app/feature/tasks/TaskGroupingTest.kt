package com.athar.app.feature.tasks

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TaskGroupingTest {
    private val today = LocalDate.of(2026, 11, 14)

    private val overdue = Task(1, "overdue", today.minusDays(2), createdAt = 1)
    private val dueToday = Task(2, "today", today, createdAt = 2)
    private val upcoming = Task(3, "upcoming", today.plusDays(3), createdAt = 3)
    private val someday = Task(4, "someday", null, createdAt = 4)
    private val doneToday = Task(5, "done today", today, done = true, completedOn = today, createdAt = 5)
    private val doneEarlier = Task(6, "done earlier", today.minusDays(1), done = true, completedOn = today.minusDays(1), createdAt = 6)
    private val all = listOf(doneEarlier, someday, upcoming, doneToday, dueToday, overdue)

    @Test
    fun sectionsInDisplayOrder() {
        val groups = TaskGrouping.group(all, today)
        assertEquals(
            listOf(TaskSection.Overdue, TaskSection.Today, TaskSection.Upcoming, TaskSection.Someday, TaskSection.Done),
            groups.map { it.first },
        )
        // Most recently completed first
        assertEquals(listOf(5L, 6L), groups.last().second.map { it.id })
    }

    @Test
    fun emptySectionsAreOmitted() {
        assertEquals(listOf(TaskSection.Today), TaskGrouping.group(listOf(dueToday), today).map { it.first })
    }

    @Test
    fun todayCardShowsOverdueTodayAndCompletedToday() {
        // Undone first (overdue before today), then done today; no upcoming/undated/older done
        assertEquals(listOf(1L, 2L, 5L), TaskGrouping.forToday(all, today).map { it.id })
    }
}
