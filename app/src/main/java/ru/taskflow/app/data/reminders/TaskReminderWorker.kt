package ru.taskflow.app.data.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

class TaskReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val taskId = inputData.getString(TASK_ID) ?: return Result.failure()
        val title = inputData.getString(TASK_TITLE) ?: "Задача"
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        createChannel(applicationContext)
        NotificationManagerCompat.from(applicationContext).notify(taskId.hashCode(), NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Срок задачи")
            .setContentText(title)
            .setAutoCancel(true)
            .build())
        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "task_due_dates"
        private const val TASK_ID = "task_id"
        private const val TASK_TITLE = "task_title"

        fun schedule(context: Context, taskId: String, title: String, dueAt: String?) {
            WorkManager.getInstance(context).cancelUniqueWork("taskflow.reminder.$taskId")
            val dueInstant = runCatching { Instant.parse(dueAt) }.getOrNull() ?: return
            val delay = Duration.between(Instant.now(), dueInstant).toMillis()
            if (delay <= 0) return
            val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(androidx.work.workDataOf(TASK_ID to taskId, TASK_TITLE to title))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("taskflow.reminder.$taskId", ExistingWorkPolicy.REPLACE, request)
        }

        private fun createChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Сроки задач", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
}
