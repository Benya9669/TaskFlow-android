package ru.taskflow.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import ru.taskflow.app.ui.TaskFlowApp
import ru.taskflow.app.ui.theme.TaskFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TaskFlowTheme {
                Surface {
                    TaskFlowApp(
                        sharedText = intent.getStringExtra(Intent.EXTRA_TEXT),
                        taskIdFromLink = intent.data?.takeIf { it.host == "task" }?.lastPathSegment,
                        verificationServerUrl = intent.data?.takeIf { it.host == "verify" }?.getQueryParameter("server"),
                        verificationToken = intent.data?.takeIf { it.host == "verify" }?.getQueryParameter("token"),
                    )
                }
            }
        }
    }
}
