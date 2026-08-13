package com.example.service

import android.content.Context
import android.util.Log
import com.example.data.db.AppDatabase
import com.example.data.db.FileEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Service/Helper class implementing the 30-day auto-purge background job
 * for files marked as isDeleted in the FileEntity database.
 */
class BinAutoPurgeService(
    private val context: Context,
    private val db: AppDatabase
) {
    private val fileDao = db.fileDao()
    private val binDao = db.binDao()

    companion object {
        private const val TAG = "BinAutoPurgeService"
        const val PURGE_RETENTION_DAYS = 30L
        const val PURGE_RETENTION_MILLIS = PURGE_RETENTION_DAYS * 24 * 60 * 60 * 1000L

        @Volatile
        private var INSTANCE: BinAutoPurgeService? = null

        fun getInstance(context: Context, db: AppDatabase): BinAutoPurgeService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BinAutoPurgeService(context.applicationContext, db).also {
                    INSTANCE = it
                }
            }
        }
    }

    /**
     * Executes the auto-purge job to permanently remove files marked as isDeleted = true
     * that are older than 30 days.
     *
     * @return Number of files purged
     */
    suspend fun purgeExpiredDeletedFiles(): Int = withContext(Dispatchers.IO) {
        val threshold = System.currentTimeMillis() - PURGE_RETENTION_MILLIS
        Log.d(TAG, "Starting 30-day auto-purge job with threshold timestamp: $threshold")

        var purgedCount = 0

        // 1. Purge expired bin items from binDao and physical ls_bin storage
        try {
            val expiredBinItems = binDao.getExpiredBinItems(threshold)
            for (binItem in expiredBinItems) {
                val physicalFile = File(binItem.filePath)
                if (physicalFile.exists()) {
                    if (physicalFile.isDirectory) {
                        physicalFile.deleteRecursively()
                    } else {
                        physicalFile.delete()
                    }
                }
                binDao.deleteBinItem(binItem.filePath)
                purgedCount++
                Log.d(TAG, "Successfully purged expired bin file: ${binItem.filePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error purging expired bin items: ${e.message}", e)
        }

        // 2. Clean up legacy fileDao expired items if present
        try {
            val expiredFiles = fileDao.getExpiredDeletedFiles(threshold)
            for (fileEntity in expiredFiles) {
                val physicalFile = File(fileEntity.path)
                if (physicalFile.exists()) {
                    if (fileEntity.isDirectory) {
                        physicalFile.deleteRecursively()
                    } else {
                        physicalFile.delete()
                    }
                }
                fileDao.deleteById(fileEntity.id)
                binDao.deleteBinItem(fileEntity.path)
                purgedCount++
            }
            fileDao.deleteExpiredFiles(threshold)
        } catch (e: Exception) {
            Log.e(TAG, "Error purging legacy deleted files: ${e.message}", e)
        }

        Log.d(TAG, "Auto-purge job completed. Total files purged: $purgedCount")
        purgedCount
    }

    /**
     * Schedules or executes the auto-purge job asynchronously in a background coroutine scope.
     */
    fun startAutoPurgeJob(scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
        scope.launch(Dispatchers.IO) {
            try {
                purgeExpiredDeletedFiles()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to complete auto-purge background job: ${e.message}", e)
            }
        }
    }
}
