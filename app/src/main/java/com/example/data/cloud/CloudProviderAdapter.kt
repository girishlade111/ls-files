package com.example.data.cloud

import com.example.data.model.CloudProviderType

interface CloudProviderAdapter : CloudProvider

data class CloudAccountInfo(
    val accountId: String,
    val providerType: CloudProviderType,
    val displayName: String,
    val email: String
)

data class CloudFileItem(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isFolder: Boolean,
    val mimeType: String,
    val providerType: CloudProviderType
)

class GoogleDriveAdapter : CloudProviderAdapter {
    override val providerType = CloudProviderType.GOOGLE_DRIVE
    private var connected: Boolean = true
    private var currentAccount: CloudAccountInfo? = null

    override suspend fun connectAccount(): CloudAccountInfo {
        val account = CloudAccountInfo(
            accountId = "gdrive_${System.currentTimeMillis()}",
            providerType = CloudProviderType.GOOGLE_DRIVE,
            displayName = "Google User",
            email = "user@gmail.com"
        )
        currentAccount = account
        connected = true
        return account
    }

    override suspend fun isConnected(): Boolean = connected

    override suspend fun disconnect() {
        connected = false
        currentAccount = null
    }

    override suspend fun listFiles(parentFolderId: String): List<CloudFileItem> {
        return listOf(
            CloudFileItem(
                id = "drive_1",
                name = "Project Proposal.pdf",
                sizeBytes = 2_450_000L,
                lastModified = System.currentTimeMillis() - 86400000L * 2,
                isFolder = false,
                mimeType = "application/pdf",
                providerType = CloudProviderType.GOOGLE_DRIVE
            ),
            CloudFileItem(
                id = "drive_2",
                name = "Shared Documents",
                sizeBytes = 0L,
                lastModified = System.currentTimeMillis() - 86400000L * 5,
                isFolder = true,
                mimeType = "folder",
                providerType = CloudProviderType.GOOGLE_DRIVE
            )
        )
    }

    override suspend fun uploadFile(localPath: String, cloudFolderId: String): CloudFileItem {
        val fileName = localPath.substringAfterLast('/')
        return CloudFileItem(
            id = "drive_up_${System.currentTimeMillis()}",
            name = fileName,
            sizeBytes = 1_000_000L,
            lastModified = System.currentTimeMillis(),
            isFolder = false,
            mimeType = "application/octet-stream",
            providerType = CloudProviderType.GOOGLE_DRIVE
        )
    }

    override suspend fun downloadFile(cloudFileId: String, targetLocalPath: String): Boolean = true
    override suspend fun deleteFile(cloudFileId: String): Boolean = true
}

class OneDriveAdapter : CloudProviderAdapter {
    override val providerType = CloudProviderType.ONEDRIVE
    private var connected: Boolean = true
    private var currentAccount: CloudAccountInfo? = null

    override suspend fun connectAccount(): CloudAccountInfo {
        val account = CloudAccountInfo(
            accountId = "onedrive_${System.currentTimeMillis()}",
            providerType = CloudProviderType.ONEDRIVE,
            displayName = "OneDrive User",
            email = "user@outlook.com"
        )
        currentAccount = account
        connected = true
        return account
    }

    override suspend fun isConnected(): Boolean = connected

    override suspend fun disconnect() {
        connected = false
        currentAccount = null
    }

    override suspend fun listFiles(parentFolderId: String): List<CloudFileItem> {
        return listOf(
            CloudFileItem(
                id = "onedrive_1",
                name = "Financial Report Q3.xlsx",
                sizeBytes = 1_850_000L,
                lastModified = System.currentTimeMillis() - 3600000L * 12,
                isFolder = false,
                mimeType = "application/vnd.ms-excel",
                providerType = CloudProviderType.ONEDRIVE
            )
        )
    }

    override suspend fun uploadFile(localPath: String, cloudFolderId: String): CloudFileItem {
        val fileName = localPath.substringAfterLast('/')
        return CloudFileItem(
            id = "onedrive_up_${System.currentTimeMillis()}",
            name = fileName,
            sizeBytes = 500_000L,
            lastModified = System.currentTimeMillis(),
            isFolder = false,
            mimeType = "application/octet-stream",
            providerType = CloudProviderType.ONEDRIVE
        )
    }

    override suspend fun downloadFile(cloudFileId: String, targetLocalPath: String): Boolean = true
    override suspend fun deleteFile(cloudFileId: String): Boolean = true
}

class DropboxAdapter : CloudProviderAdapter {
    override val providerType = CloudProviderType.DROPBOX
    private var connected: Boolean = true
    private var currentAccount: CloudAccountInfo? = null

    override suspend fun connectAccount(): CloudAccountInfo {
        val account = CloudAccountInfo(
            accountId = "dropbox_${System.currentTimeMillis()}",
            providerType = CloudProviderType.DROPBOX,
            displayName = "Dropbox User",
            email = "user@dropbox.com"
        )
        currentAccount = account
        connected = true
        return account
    }

    override suspend fun isConnected(): Boolean = connected

    override suspend fun disconnect() {
        connected = false
        currentAccount = null
    }

    override suspend fun listFiles(parentFolderId: String): List<CloudFileItem> {
        return listOf(
            CloudFileItem(
                id = "dropbox_1",
                name = "Backup_Archive.zip",
                sizeBytes = 14_200_000L,
                lastModified = System.currentTimeMillis() - 86400000L * 10,
                isFolder = false,
                mimeType = "application/zip",
                providerType = CloudProviderType.DROPBOX
            )
        )
    }

    override suspend fun uploadFile(localPath: String, cloudFolderId: String): CloudFileItem {
        val fileName = localPath.substringAfterLast('/')
        return CloudFileItem(
            id = "dropbox_up_${System.currentTimeMillis()}",
            name = fileName,
            sizeBytes = 800_000L,
            lastModified = System.currentTimeMillis(),
            isFolder = false,
            mimeType = "application/octet-stream",
            providerType = CloudProviderType.DROPBOX
        )
    }

    override suspend fun downloadFile(cloudFileId: String, targetLocalPath: String): Boolean = true
    override suspend fun deleteFile(cloudFileId: String): Boolean = true
}
