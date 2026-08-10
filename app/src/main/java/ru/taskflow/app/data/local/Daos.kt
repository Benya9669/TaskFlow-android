package ru.taskflow.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL ORDER BY scheduledDate IS NULL, scheduledDate, kanbanPosition, updatedAt DESC")
    fun observeActive(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun find(id: String): TaskEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(task: TaskEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(tasks: List<TaskEntity>)
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE deletedAt IS NULL AND archivedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<ProjectEntity>>
    @Query("SELECT * FROM projects WHERE deletedAt IS NULL AND archivedAt IS NOT NULL ORDER BY name COLLATE NOCASE")
    fun observeArchived(): Flow<List<ProjectEntity>>
    @Query("SELECT * FROM projects WHERE id = :id") suspend fun find(id: String): ProjectEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(project: ProjectEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(projects: List<ProjectEntity>)
}

@Dao
interface KanbanColumnDao {
    @Query("SELECT * FROM kanban_columns WHERE deletedAt IS NULL ORDER BY position")
    fun observeActive(): Flow<List<KanbanColumnEntity>>
    @Query("SELECT * FROM kanban_columns WHERE deletedAt IS NULL AND semanticStatus = 'inbox' ORDER BY position LIMIT 1")
    suspend fun inbox(): KanbanColumnEntity?
    @Query("SELECT * FROM kanban_columns WHERE deletedAt IS NULL AND semanticStatus = :status ORDER BY position LIMIT 1")
    suspend fun byStatus(status: String): KanbanColumnEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(columns: List<KanbanColumnEntity>)
}

@Dao
interface MutationDao {
    @Query("SELECT * FROM pending_mutations ORDER BY createdAt LIMIT :limit") suspend fun nextBatch(limit: Int): List<PendingMutationEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(mutation: PendingMutationEntity)
    @Query("DELETE FROM pending_mutations WHERE id IN (:ids)") suspend fun delete(ids: List<String>)
}

@Dao
interface SyncStateDao {
    @Query("SELECT cursor FROM sync_state WHERE key = :key") suspend fun cursor(key: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(state: SyncStateEntity)
}

@Dao
interface TaskConflictDao {
    @Query("SELECT * FROM task_conflicts ORDER BY createdAt") fun observeAll(): Flow<List<TaskConflictEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(conflict: TaskConflictEntity)
    @Query("DELETE FROM task_conflicts WHERE mutationId = :mutationId") suspend fun delete(mutationId: String)
}
