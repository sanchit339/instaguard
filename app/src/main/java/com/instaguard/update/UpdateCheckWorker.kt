package com.instaguard.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.instaguard.BuildConfig

class UpdateCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val result = GitHubUpdateChecker.check(BuildConfig.VERSION_NAME)
        return result.fold(
            onSuccess = { info ->
                if (info.isUpdateAvailable) {
                    UpdateNotifier.notifyUpdateAvailable(
                        context = applicationContext,
                        latestTag = info.latestTag,
                        releaseUrl = info.releaseUrl
                    )
                }
                Result.success()
            },
            onFailure = {
                Result.retry()
            }
        )
    }
}
