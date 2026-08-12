package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "files")
data class FileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val path: String,
    val size: Long,
    val mimeType: String,
    val lastModified: Long,
    val isDirectory: Boolean,
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "indexed_files",
    indices = [
        Index(value = ["fileName"]),
        Index(value = ["parentPath"]),
        Index(value = ["categoryName"]),
        Index(value = ["lastModified"])
    ]
)
data class IndexedFileEntity(
    @PrimaryKey val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val lastModified: Long,
    val mimeType: String,
    val categoryName: String,
    val isDirectory: Boolean,
    val parentPath: String,
    val ocrText: String? = null,
    val indexedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "starred_files")
data class StarredFileEntity(
    @PrimaryKey val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val lastModified: Long,
    val mimeType: String,
    val isDirectory: Boolean
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val tagId: Long = 0,
    val tagName: String,
    val tagColorHex: String,
    val tagIconName: String = "label"
)

@Entity(tableName = "file_tag_cross_ref", primaryKeys = ["filePath", "tagId"])
data class FileTagCrossRef(
    val filePath: String,
    val tagId: Long
)

@Entity(tableName = "bin_items")
data class BinItemEntity(
    @PrimaryKey val filePath: String,
    val fileName: String,
    val originalPath: String,
    val fileSize: Long,
    val mimeType: String,
    val deletedTimestamp: Long
)

@Entity(tableName = "search_history")
data class SearchQueryEntity(
    @PrimaryKey val query: String,
    val timestamp: Long
)

@Entity(tableName = "cloud_accounts")
data class CloudAccountEntity(
    @PrimaryKey val accountId: String,
    val providerType: String,
    val accountName: String,
    val accountEmail: String,
    val connectedTimestamp: Long
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String
)
