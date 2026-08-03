package ru.taskflow.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val projectId: String?,
    val columnId: String,
    val title: String,
    val description: String,
    val status: String,
    val priority: String,
    val scheduledDate: String?,
    val dueAt: String?,
    val estimatedMinutes: Int?,
    val kanbanPosition: Int,
    val recurrence: String?,
    val reminderOffsets: List<Int>,
    val tags: List<String>,
    val createdAt: String,
    val updatedAt: String,
    val version: Int,
    val deletedAt: String?,
)

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val name: String,
    val color: String,
    val createdAt: String,
    val updatedAt: String,
    val version: Int,
    val deletedAt: String?,
    val archivedAt: String?,
)

@Entity(tableName = "kanban_columns")
data class KanbanColumnEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val name: String,
    val color: String,
    val semanticStatus: String,
    val position: Int,
    val createdAt: String,
    val updatedAt: String,
    val version: Int,
    val deletedAt: String?,
)

@Entity(tableName = "pending_mutations")
data class PendingMutationEntity(
    @PrimaryKey val id: String,
    val operation: String,
    val taskId: String,
    val bodyJson: String?,
    val createdAt: Long,
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val key: String,
    val cursor: String,
)

@Entity(tableName = "task_conflicts")
data class TaskConflictEntity(
    @PrimaryKey val mutationId: String,
    val taskId: String,
    val localBodyJson: String,
    val serverTitle: String,
    val serverPriority: String,
    val createdAt: Long,
)

class TaskFlowConverters {
    private val moshi = Moshi.Builder().build()
    private val intList = moshi.adapter<List<Int>>(Types.newParameterizedType(List::class.java, Int::class.javaObjectType))
    private val stringList = moshi.adapter<List<String>>(Types.newParameterizedType(List::class.java, String::class.java))

    @TypeConverter fun encodeIntList(value: List<Int>): String = intList.toJson(value)
    @TypeConverter fun decodeIntList(value: String): List<Int> = intList.fromJson(value).orEmpty()
    @TypeConverter fun encodeStringList(value: List<String>): String = stringList.toJson(value)
    @TypeConverter fun decodeStringList(value: String): List<String> = stringList.fromJson(value).orEmpty()
}
