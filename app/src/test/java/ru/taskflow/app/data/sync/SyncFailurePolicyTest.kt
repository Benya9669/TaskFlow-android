package ru.taskflow.app.data.sync

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncFailurePolicyTest {
    @Test fun retriesNetworkFailures() = assertTrue(SyncFailurePolicy.shouldRetry(IOException()))
    @Test fun doesNotRetryUnexpectedFailures() = assertFalse(SyncFailurePolicy.shouldRetry(IllegalStateException()))
}
