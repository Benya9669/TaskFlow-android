package ru.taskflow.app.data

import ru.taskflow.app.data.remote.TaskFlowApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

class SyncRepository(private val api: TaskFlowApi, private val tasks: TaskRepository) {
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
                        ru.taskflow.app.data.remote.MutationDto(mutation.id, mutation.operation, mutation.taskId, mutation.bodyJson?.let(mapAdapter::fromJson))
                    },
                ),
            )
            tasks.removeMutations(response.mutations.filter { it.status in 200..299 }.map { it.id })
        }
        pull()
    }

    private companion object {
        const val MAX_MUTATIONS = 100
        val moshi = Moshi.Builder().build()
    }
}
