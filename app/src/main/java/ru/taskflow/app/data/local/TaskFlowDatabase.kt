package ru.taskflow.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [TaskEntity::class, ProjectEntity::class, KanbanColumnEntity::class, PendingMutationEntity::class, SyncStateEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(TaskFlowConverters::class)
abstract class TaskFlowDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun projectDao(): ProjectDao
    abstract fun kanbanColumnDao(): KanbanColumnDao
    abstract fun mutationDao(): MutationDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        @Volatile private var instance: TaskFlowDatabase? = null

        fun get(context: Context): TaskFlowDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TaskFlowDatabase::class.java, "taskflow.db")
                .build()
                .also { instance = it }
        }
    }
}
