package ru.taskflow.app.data

data class TaskUpdate(
    val title: String,
    val priority: String,
    val description: String,
    val projectId: String?,
    val scheduledDate: String?,
    val dueAt: String?,
    val estimatedMinutes: Int?,
    val tags: List<String>,
    val reminderOffsets: List<Int>,
)
