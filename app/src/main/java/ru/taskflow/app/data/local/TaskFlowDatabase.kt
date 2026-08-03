package ru.taskflow.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.TypeConverters

@Database(
    entities = [TaskEntity::class, ProjectEntity::class, KanbanColumnEntity::class, PendingMutationEntity::class, SyncStateEntity::class, TaskConflictEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(TaskFlowConverters::class)
abstract class TaskFlowDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun projectDao(): ProjectDao
    abstract fun kanbanColumnDao(): KanbanColumnDao
    abstract fun mutationDao(): MutationDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun taskConflictDao(): TaskConflictDao

    companion object {
        @Volatile private var instance: TaskFlowDatabase? = null

        fun get(context: Context): TaskFlowDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TaskFlowDatabase::class.java, "taskflow.db")
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS task_conflicts (mutationId TEXT NOT NULL, taskId TEXT NOT NULL, localBodyJson TEXT NOT NULL, serverTitle TEXT NOT NULL, serverPriority TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(mutationId))")
            }
        }
    }
}
