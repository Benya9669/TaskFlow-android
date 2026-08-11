package ru.taskflow.app.data

import ru.taskflow.app.data.remote.TaskFlowApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class SyncRepository(private val api: TaskFlowApi, private val tasks: TaskRepository, private val projects: ProjectRepository? = null) {
    suspend fun pull() {
        var cursor: String? = null
        var snapshot: String? = null
        var since = tasks.syncCursor()
        do {
            val page = api.sync(since = since, cursor = cursor, snapshot = snapshot)
            tasks.applySyncPage(page.tasks, page.projects, page.kanbanColumns, page.cursor)
            since = page.cursor
            cursor = page.nextCursor
            snapshot = page.snapshot
        } while (page.hasMore && cursor != null)
    }

    suspend fun pushAndPull() {
        val pending = tasks.pendingMutations(MAX_MUTATIONS)
        if (pending.isNotEmpty()) {
            val mapAdapter = moshi.adapter<Map<String, Any?>>(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))
            val response = api.sendMutations(
                ru.taskflow.app.data.remote.MutationBatch(
                    pending.map { mutation ->
                        ru.taskflow.app.data.remote.MutationDto(
                            mutation.id,
                            mutation.operation,
                            mutation.taskId.takeIf { mutation.entityType == "task" },
                            mutation.bodyJson?.let(mapAdapter::fromJson)?.let(::normalizeJsonNumbers) as? Map<String, Any?>,
                            mutation.entityType,
                            mutation.taskId.takeIf { mutation.entityType == "project" },
                        )
                    },
                ),
            )
            val byId = pending.associateBy { it.id }
            tasks.removeMutations(response.mutations.filter { it.status in 200..299 }.map { it.id })
            response.mutations.filter { it.status == 409 }.forEach { result ->
                val mutation = byId[result.id] ?: return@forEach
                if (mutation.entityType == "project") {
                    val currentProject = result.response["current_project"] as? Map<*, *> ?: return@forEach
                    val normalized = currentProject.entries.associate { (key, value) -> key.toString() to value }
                    projectAdapter.fromJson(mapAdapter.toJson(normalized))?.let { projects?.saveConflict(mutation, it) }
                } else {
                    val currentTask = result.response["current_task"] as? Map<*, *> ?: return@forEach
                    val normalizedTask = currentTask.entries.associate { (key, value) -> key.toString() to value }
                    taskAdapter.fromJson(mapAdapter.toJson(normalizedTask))?.let { tasks.saveConflict(mutation, it) }
                }
            }
        }
        pull()
    }

    private companion object {
        const val MAX_MUTATIONS = 100
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val mapAdapter = moshi.adapter<Map<String, Any?>>(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))
        val taskAdapter = moshi.adapter(ru.taskflow.app.data.remote.TaskDto::class.java)
        val projectAdapter = moshi.adapter(ru.taskflow.app.data.remote.ProjectDto::class.java)
    }
}

private fun normalizeJsonNumbers(value: Any?): Any? = when (value) {
    is Double -> when {
        !value.isFinite() || value % 1.0 != 0.0 -> value
        value in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() -> value.toInt()
        else -> value.toLong()
    }
    is List<*> -> value.map(::normalizeJsonNumbers)
    is Map<*, *> -> value.entries.associate { (key, item) -> key.toString() to normalizeJsonNumbers(item) }
    else -> value
}
