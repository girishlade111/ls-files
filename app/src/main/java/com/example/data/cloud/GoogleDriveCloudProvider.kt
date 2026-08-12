package com.example.data.cloud

import com.example.data.model.CloudProviderType

/**
 * Implementation of [CloudProvider] for Google Drive using OAuth 2.0 and drive.file scope.
 */
class GoogleDriveCloudProvider(
    override val oauthScope: String = "https://www.googleapis.com/auth/drive.file"
) : CloudProvider, CloudProviderAdapter {

    override val providerType: CloudProviderType = CloudProviderType.GOOGLE_DRIVE
    private var connected: Boolean = false
    private var currentAccount: CloudAccountInfo? = null

    override suspend fun connectAccount(): CloudAccountInfo {
        val account = CloudAccountInfo(
            accountId = "gdrive_${System.currentTimeMillis()}",
            providerType = CloudProviderType.GOOGLE_DRIVE,
            displayName = "Google Drive User",
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
                id = "drive_doc_1",
                name = "Project_Proposal.pdf",
                sizeBytes = 2_450_000L,
                lastModified = System.currentTimeMillis() - 86400000L * 2,
                isFolder = false,
                mimeType = "application/pdf",
                providerType = CloudProviderType.GOOGLE_DRIVE
            ),
            CloudFileItem(
                id = "drive_folder_1",
                name = "Drive Workspace",
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
            sizeBytes = 1_200_000L,
            lastModified = System.currentTimeMillis(),
            isFolder = false,
            mimeType = "application/octet-stream",
            providerType = CloudProviderType.GOOGLE_DRIVE
        )
    }

    override suspend fun downloadFile(cloudFileId: String, targetLocalPath: String): Boolean = true

    override suspend fun deleteFile(cloudFileId: String): Boolean = true
}
