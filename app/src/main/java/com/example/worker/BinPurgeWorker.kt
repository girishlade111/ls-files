package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.db.AppDatabase
import com.example.service.BinAutoPurgeService
import java.util.concurrent.TimeUnit

/**
 * WorkManager CoroutineWorker that periodically performs a background job
 * to delete files marked as isDeleted (recycle bin items) where modification
 * timestamp is older than 30 days.
 */
class BinPurgeWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "BinPurgePeriodicWorker"
        private const val TAG = "BinPurgeWorker"

        /**
         * Enqueues a 24-hour periodic work request to automatically purge expired bin items.
         */
        fun schedulePeriodicPurgeWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val purgeWorkRequest = PeriodicWorkRequestBuilder<BinPurgeWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                purgeWorkRequest
            )

            Log.d(TAG, "Scheduled periodic 30-day bin auto-purge work request.")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Executing periodic bin auto-purge worker...")
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val autoPurgeService = BinAutoPurgeService.getInstance(applicationContext, db)
            val count = autoPurgeService.purgeExpiredDeletedFiles()
            Log.d(TAG, "Bin auto-purge completed successfully. Total items purged: $count")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing bin auto-purge worker: ${e.message}", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
