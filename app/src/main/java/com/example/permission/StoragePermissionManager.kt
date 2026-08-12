package com.example.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Handles storage permission management for Android 11+ (MANAGE_EXTERNAL_STORAGE)
 * and fallback strategies for older Android versions (READ/WRITE_EXTERNAL_STORAGE).
 */
class StoragePermissionManager(private val context: Context) {

    companion object {
        val LEGACY_PERMISSIONS = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        fun isAndroid11OrHigher(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    /**
     * Checks if the app has required storage access permissions.
     * Returns true if MANAGE_EXTERNAL_STORAGE is granted on Android 11+,
     * or READ/WRITE_EXTERNAL_STORAGE are granted on older versions.
     */
    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val readPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

            val writePermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

            readPermission && writePermission
        }
    }

    /**
     * Creates an Intent to launch system settings for granting storage access.
     */
    fun createManageStorageIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            } catch (e: Exception) {
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }
    }

    /**
     * Requests storage permission by starting the appropriate settings or permission activity.
     */
    fun requestStoragePermission() {
        if (hasStoragePermission()) return

        val intent = createManageStorageIntent().apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
