package com.athar.app.feature.tasks

import java.time.LocalDate

data class Task(
    val id: Long = 0,
    val title: String,
    val due: LocalDate?,
    val done: Boolean = false,
    val completedOn: LocalDate? = null,
    val createdAt: Long = 0,
) {
    val isNew: Boolean get() = id == 0L

    companion object {
        fun new(today: LocalDate) = Task(title = "", due = today)
    }
}

enum class TaskSection { Overdue, Today, Upcoming, Someday, Done }

object TaskGrouping {
    fun sectionOf(task: Task, today: LocalDate): TaskSection {
        val due = task.due
        return when {
            task.done -> TaskSection.Done
            due == null -> TaskSection.Someday
            due.isBefore(today) -> TaskSection.Overdue
            due == today -> TaskSection.Today
            else -> TaskSection.Upcoming
        }
    }

    /** Non-empty sections in display order. */
    fun group(tasks: List<Task>, today: LocalDate): List<Pair<TaskSection, List<Task>>> {
        val bySection = tasks.groupBy { sectionOf(it, today) }
        return TaskSection.entries.mapNotNull { section ->
            val items = bySection[section].orEmpty()
            if (items.isEmpty()) {
                null
            } else {
                val sorted = if (section == TaskSection.Done) {
                    items.sortedByDescending { it.completedOn }
                } else {
                    items.sortedWith(compareBy<Task>({ it.due == null }, { it.due }, { it.createdAt }))
                }
                section to sorted
            }
        }
    }

    /** For the home card: overdue + due today, plus what was completed today. Undone first. */
    fun forToday(tasks: List<Task>, today: LocalDate): List<Task> =
        tasks.filter { task ->
            val due = task.due
            if (task.done) task.completedOn == today else due != null && !due.isAfter(today)
        }.sortedWith(compareBy<Task> { it.done }.thenBy { it.due }.thenBy { it.createdAt })
}
