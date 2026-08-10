package ru.taskflow.app.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskFlowDatabaseMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TaskFlowDatabase::class.java.canonicalName!!, FrameworkSQLiteOpenHelperFactory())

    @Test fun migratesV1ToV3WithoutDeletingTasksOrOutbox() {
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL("INSERT INTO tasks (id, ownerId, projectId, columnId, title, description, status, priority, scheduledDate, dueAt, estimatedMinutes, kanbanPosition, recurrence, reminderOffsets, tags, createdAt, updatedAt, version, deletedAt) VALUES ('task-1', 'owner', NULL, 'column', 'Saved task', '', 'inbox', 'normal', NULL, NULL, NULL, 0, NULL, '[]', '[]', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z', 1, NULL)")
            execSQL("INSERT INTO pending_mutations (id, operation, taskId, bodyJson, createdAt) VALUES ('mutation-1', 'create', 'task-1', NULL, 1)")
            close()
        }
        val database = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, TaskFlowDatabase::class.java, DB_NAME).addMigrations(TaskFlowDatabase.migration1To2, TaskFlowDatabase.migration2To3).build()
        database.openHelper.writableDatabase
        database.openHelper.readableDatabase.query("SELECT title FROM tasks WHERE id = 'task-1'").use { cursor -> cursor.moveToFirst(); assertEquals("Saved task", cursor.getString(0)) }
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM pending_mutations").use { cursor -> cursor.moveToFirst(); assertEquals(1, cursor.getInt(0)) }
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM task_conflicts").use { cursor -> cursor.moveToFirst(); assertEquals(0, cursor.getInt(0)) }
        database.openHelper.readableDatabase.query("SELECT entityType FROM pending_mutations").use { cursor -> cursor.moveToFirst(); assertEquals("task", cursor.getString(0)) }
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM project_conflicts").use { cursor -> cursor.moveToFirst(); assertEquals(0, cursor.getInt(0)) }
        database.close()
    }

    private companion object { const val DB_NAME = "migration-test" }
}
