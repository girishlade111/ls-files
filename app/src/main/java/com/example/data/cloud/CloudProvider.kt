package com.example.data.cloud

import com.example.data.model.CloudProviderType

interface CloudProvider {
    val providerType: CloudProviderType
    val oauthScope: String get() = "https://www.googleapis.com/auth/drive.file"
    
    suspend fun connectAccount(): CloudAccountInfo
    suspend fun isConnected(): Boolean = true
    suspend fun disconnect() {}
    suspend fun listFiles(parentFolderId: String = "root"): List<CloudFileItem>
    suspend fun uploadFile(localPath: String, cloudFolderId: String): CloudFileItem
    suspend fun downloadFile(cloudFileId: String, targetLocalPath: String): Boolean
    suspend fun deleteFile(cloudFileId: String): Boolean
}
