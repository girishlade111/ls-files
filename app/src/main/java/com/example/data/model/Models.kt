package com.example.data.model

enum class FileCategory(val displayName: String) {
    DOWNLOADS("Downloads"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Documents"),
    APPS("Apps"),
    SCREENSHOTS("Screenshots"),
    ARCHIVES("Archives"),
    OTHER("Other")
}

enum class SortField {
    NAME, DATE, SIZE, TYPE
}

enum class SortOrder {
    ASCENDING, DESCENDING
}

enum class ViewMode {
    LIST, GRID
}

enum class CloudProviderType(val displayName: String) {
    GOOGLE_DRIVE("Google Drive"),
    ONEDRIVE("Microsoft OneDrive"),
    DROPBOX("Dropbox")
}

data class FileItem(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val mimeType: String = "",
    val category: FileCategory = FileCategory.OTHER,
    val childCount: Int = 0,
    val isStarred: Boolean = false,
    val isHidden: Boolean = false,
    val cloudProvider: CloudProviderType? = null,
    val tags: List<TagItem> = emptyList(),
    val ocrText: String? = null
)

data class TagItem(
    val id: Long = 0,
    val name: String,
    val colorHex: String,
    val iconName: String = "label"
)

data class BinItem(
    val filePath: String,
    val fileName: String,
    val originalPath: String,
    val fileSize: Long,
    val mimeType: String,
    val deletedTimestamp: Long
) {
    val daysRemaining: Int
        get() {
            val millisInDay = 86400000L
            val elapsed = System.currentTimeMillis() - deletedTimestamp
            val remaining = 30 - (elapsed / millisInDay).toInt()
            return remaining.coerceAtLeast(0)
        }
}

data class StorageSpaceInfo(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long
) {
    val freeGb: Double
        get() = freeBytes / (1024.0 * 1024.0 * 1024.0)
    val totalGb: Double
        get() = totalBytes / (1024.0 * 1024.0 * 1024.0)
    val usedRatio: Float
        get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes) else 0f
}
