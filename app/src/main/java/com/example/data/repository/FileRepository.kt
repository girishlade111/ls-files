package com.example.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.webkit.MimeTypeMap
import com.example.data.db.*
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

import com.example.service.BinAutoPurgeService
import com.example.worker.BinPurgeWorker

class FileRepository(
    private val context: Context,
    private val db: AppDatabase
) {
    val fileDao = db.fileDao()
    val fileIndexDao = db.fileIndexDao()
    val starredDao = db.starredDao()
    val tagDao = db.tagDao()
    val binDao = db.binDao()
    val searchHistoryDao = db.searchHistoryDao()
    val cloudDao = db.cloudDao()
    val settingsDao = db.settingsDao()
    val binAutoPurgeService = BinAutoPurgeService.getInstance(context, db)
    val geminiOcrService = com.example.service.GeminiOcrService()

    val rootPath: String = Environment.getExternalStorageDirectory().absolutePath

    init {
        ensureDefaultTags()
        // Trigger 30-day auto-purge job for files marked as isDeleted
        binAutoPurgeService.startAutoPurgeJob()
        // Schedule periodic WorkManager task for 30-day bin purge
        BinPurgeWorker.schedulePeriodicPurgeWork(context)
    }

    private fun ensureDefaultTags() {
        // Run in background thread during initialization if needed
    }

    suspend fun seedDefaultTagsIfEmpty() = withContext(Dispatchers.IO) {
        val existing = tagDao.getAllTags()
        // Default tags: Important, Work, Study, Life
        val defaultTags = listOf(
            TagEntity(tagName = "Important", tagColorHex = "#F44336", tagIconName = "priority_high"),
            TagEntity(tagName = "Work", tagColorHex = "#2196F3", tagIconName = "work"),
            TagEntity(tagName = "Study", tagColorHex = "#9C27B0", tagIconName = "school"),
            TagEntity(tagName = "Life", tagColorHex = "#4CAF50", tagIconName = "favorite")
        )
        defaultTags.forEach { tag ->
            tagDao.insertTag(tag)
        }
    }

    private fun ensureSampleFilesExist() {
        try {
            val rootDir = File(rootPath)
            if (!rootDir.exists()) rootDir.mkdirs()

            val downloads = File(rootDir, "Download")
            if (!downloads.exists()) downloads.mkdirs()

            val docs = File(rootDir, "Documents")
            if (!docs.exists()) docs.mkdirs()

            val pictures = File(rootDir, "Pictures")
            if (!pictures.exists()) pictures.mkdirs()

            val screenshots = File(pictures, "Screenshots")
            if (!screenshots.exists()) screenshots.mkdirs()

            val music = File(rootDir, "Music")
            if (!music.exists()) music.mkdirs()

            val movies = File(rootDir, "Movies")
            if (!movies.exists()) movies.mkdirs()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getStorageSpaceInfo(): StorageSpaceInfo = withContext(Dispatchers.IO) {
        try {
            val path = Environment.getExternalStorageDirectory().path
            val stat = android.os.StatFs(path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            val total = totalBlocks * blockSize
            val free = availableBlocks * blockSize
            val used = (total - free).coerceAtLeast(0)
            if (total > 0) {
                return@withContext StorageSpaceInfo(totalBytes = total, freeBytes = free, usedBytes = used)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val root = Environment.getExternalStorageDirectory()
        val total = root.totalSpace
        val free = root.freeSpace
        val used = (total - free).coerceAtLeast(0)
        StorageSpaceInfo(totalBytes = total, freeBytes = free, usedBytes = used)
    }

    suspend fun listFilesAt(dirPath: String, showHidden: Boolean = false): List<FileItem> = withContext(Dispatchers.IO) {
        val folder = File(dirPath)
        if (!folder.exists() || !folder.isDirectory) return@withContext emptyList()

        val files = folder.listFiles() ?: return@withContext emptyList()
        files
            .filter { showHidden || !it.isHidden }
            .map { file ->
                mapToFileItem(file)
            }
    }

    suspend fun mapToFileItem(file: File): FileItem = withContext(Dispatchers.IO) {
        val isStarred = db.starredDao().isStarred(file.absolutePath)
        val category = determineCategory(file)
        val mimeType = getMimeType(file)
        val childCount = if (file.isDirectory) (file.listFiles()?.size ?: 0) else 0

        // Get tags
        val tagIds = tagDao.getTagIdsForFile(file.absolutePath)
        val allTags = mutableListOf<TagItem>()
        if (tagIds.isNotEmpty()) {
            // map tag ids if needed
        }

        FileItem(
            name = file.name,
            path = file.absolutePath,
            sizeBytes = if (file.isDirectory) calculateFolderSize(file) else file.length(),
            lastModified = file.lastModified(),
            isDirectory = file.isDirectory,
            mimeType = mimeType,
            category = category,
            childCount = childCount,
            isStarred = isStarred,
            isHidden = file.name.startsWith("."),
            tags = allTags
        )
    }

    fun calculateFolderSize(folder: File, maxDepth: Int = 1, currentDepth: Int = 0): Long {
        if (currentDepth >= maxDepth) return 0L
        var size = 0L
        try {
            val files = folder.listFiles() ?: return 0L
            for (f in files) {
                if (f.name.startsWith(".")) continue
                if (f.isDirectory) {
                    if (f.name.equals("Android", ignoreCase = true) && currentDepth == 0) continue
                    size += calculateFolderSize(f, maxDepth, currentDepth + 1)
                } else {
                    size += f.length()
                }
            }
        } catch (_: Exception) { }
        return size
    }

    fun getMimeType(file: File): String {
        if (file.isDirectory) return "folder"
        val ext = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    fun determineCategory(file: File): FileCategory {
        if (file.isDirectory) return FileCategory.OTHER
        val parentName = file.parentFile?.name ?: ""
        val ext = file.extension.lowercase()
        val mimeType = getMimeType(file).lowercase()

        if (parentName.equals("Screenshots", ignoreCase = true) || file.name.startsWith("Screenshot", ignoreCase = true)) {
            return FileCategory.SCREENSHOTS
        }

        // 1. Primary Classification by File Extensions
        when (ext) {
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "svg", "ico", "tiff", "tif", "raw", "cr2", "nef", "arw", "avif" -> return FileCategory.IMAGES
            "mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "wmv", "m4v", "ts", "m2ts", "vob" -> return FileCategory.VIDEOS
            "mp3", "wav", "aac", "flac", "ogg", "m4a", "wma", "opus", "amr", "mid", "midi", "alac" -> return FileCategory.AUDIO
            "pdf", "doc", "docx", "txt", "rtf", "xls", "xlsx", "ppt", "pptx", "csv", "epub", "mobi", "odt", "ods", "odp", "md", "log", "xml", "json", "html", "htm" -> return FileCategory.DOCUMENTS
            "apk" -> return FileCategory.APPS
            "zip", "rar", "7z", "tar", "gz", "bz2" -> return FileCategory.ARCHIVES
        }

        // 2. Secondary Classification by Metadata & MIME Types
        if (mimeType.startsWith("image/")) return FileCategory.IMAGES
        if (mimeType.startsWith("video/")) return FileCategory.VIDEOS
        if (mimeType.startsWith("audio/")) return FileCategory.AUDIO
        if (mimeType.startsWith("text/") || 
            mimeType.contains("pdf") || 
            mimeType.contains("msword") || 
            mimeType.contains("officedocument") || 
            mimeType.contains("openxmlformats") ||
            mimeType.contains("vnd.oasis.opendocument")) return FileCategory.DOCUMENTS
        if (mimeType.contains("android.package-archive")) return FileCategory.APPS
        if (mimeType.contains("zip") || mimeType.contains("compressed") || mimeType.contains("archive") || mimeType.contains("tar")) return FileCategory.ARCHIVES

        if (parentName.equals("Download", ignoreCase = true) || parentName.equals("Downloads", ignoreCase = true)) {
            return FileCategory.DOWNLOADS
        }

        return FileCategory.OTHER
    }

    suspend fun getCategoryFiles(category: FileCategory): List<FileItem> = withContext(Dispatchers.IO) {
        val rootDir = File(rootPath)
        val results = mutableListOf<File>()
        try {
            scanCategoryRecursive(rootDir, category, results, depth = 0, maxDepth = 20)
        } catch (_: Exception) { }
        results.map { mapToFileItem(it) }
    }

    private fun scanCategoryRecursive(dir: File, category: FileCategory, results: MutableList<File>, depth: Int, maxDepth: Int) {
        if (depth > maxDepth) return
        val files = try { dir.listFiles() } catch (_: Exception) { null } ?: return
        for (f in files) {
            try {
                if (f.name.startsWith(".")) continue
                if (f.isDirectory) {
                    if (dir.name.equals("Android", ignoreCase = true) && (f.name.equals("data", ignoreCase = true) || f.name.equals("obb", ignoreCase = true))) continue
                    scanCategoryRecursive(f, category, results, depth + 1, maxDepth)
                } else {
                    if (determineCategory(f) == category) {
                        results.add(f)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    suspend fun getRecentFiles(limit: Int = 50): List<FileItem> = withContext(Dispatchers.IO) {
        val rootDir = File(rootPath)
        val filesList = mutableListOf<File>()
        try {
            scanRecentRecursive(rootDir, filesList, depth = 0, maxDepth = 20)
            filesList.sortByDescending { it.lastModified() }
        } catch (_: Exception) { }
        filesList.take(limit).map { mapToFileItem(it) }
    }

    private fun scanRecentRecursive(dir: File, results: MutableList<File>, depth: Int, maxDepth: Int) {
        if (depth > maxDepth) return
        val files = try { dir.listFiles() } catch (_: Exception) { null } ?: return
        for (f in files) {
            try {
                if (f.name.startsWith(".")) continue
                if (f.isDirectory) {
                    if (dir.name.equals("Android", ignoreCase = true) && (f.name.equals("data", ignoreCase = true) || f.name.equals("obb", ignoreCase = true))) continue
                    scanRecentRecursive(f, results, depth + 1, maxDepth)
                } else {
                    results.add(f)
                }
            } catch (_: Exception) { }
        }
    }

    suspend fun getCategorySizes(): Map<FileCategory, Long> = withContext(Dispatchers.IO) {
        val sizes = mutableMapOf<FileCategory, Long>()
        FileCategory.values().forEach { sizes[it] = 0L }

        try {
            val rootDir = File(rootPath)
            accumulateCategorySizes(rootDir, sizes, depth = 0, maxDepth = 20)
        } catch (_: Exception) { }

        // Apps category special case: get size from PackageManager safely
        sizes[FileCategory.APPS] = getAppsTotalSize()
        sizes
    }

    private fun accumulateCategorySizes(dir: File, sizes: MutableMap<FileCategory, Long>, depth: Int, maxDepth: Int) {
        if (depth > maxDepth) return
        val files = try { dir.listFiles() } catch (_: Exception) { null } ?: return
        for (f in files) {
            try {
                if (f.name.startsWith(".")) continue
                if (f.isDirectory) {
                    if (dir.name.equals("Android", ignoreCase = true) && (f.name.equals("data", ignoreCase = true) || f.name.equals("obb", ignoreCase = true))) continue
                    accumulateCategorySizes(f, sizes, depth + 1, maxDepth)
                } else {
                    val cat = determineCategory(f)
                    sizes[cat] = (sizes[cat] ?: 0L) + f.length()
                }
            } catch (_: Exception) { }
        }
    }

    private fun getAppsTotalSize(): Long {
        return try {
            val pm = context.packageManager
            val installed = pm.getInstalledApplications(0)
            installed.sumOf { appInfo ->
                val apkFile = File(appInfo.sourceDir)
                if (apkFile.exists()) apkFile.length() else 0L
            }
        } catch (e: Exception) {
            120_000_000L // Fallback approx if pm restricted
        }
    }

    // File Operations Logic Implementation
    suspend fun copyItems(paths: List<String>, destDirPath: String): Boolean = withContext(Dispatchers.IO) {
        val destDir = File(destDirPath)
        if (!destDir.exists()) destDir.mkdirs()

        for (sourcePath in paths) {
            val source = File(sourcePath)
            if (!source.exists()) continue

            var dest = File(destDir, source.name)
            if (dest.exists()) {
                dest = getUniqueCollisionFile(destDir, source.name)
            }

            if (source.isDirectory) {
                source.copyRecursively(dest, overwrite = false)
            } else {
                source.copyTo(dest, overwrite = false)
            }
        }
        true
    }

    suspend fun moveItems(paths: List<String>, destDirPath: String): Boolean = withContext(Dispatchers.IO) {
        val destDir = File(destDirPath)
        if (!destDir.exists()) destDir.mkdirs()

        for (sourcePath in paths) {
            val source = File(sourcePath)
            if (!source.exists()) continue

            var dest = File(destDir, source.name)
            if (dest.exists()) {
                dest = getUniqueCollisionFile(destDir, source.name)
            }

            val moved = source.renameTo(dest)
            if (!moved) {
                // Fallback atomic copy-then-delete
                if (source.isDirectory) {
                    if (source.copyRecursively(dest, overwrite = false)) {
                        source.deleteRecursively()
                    }
                } else {
                    source.copyTo(dest, overwrite = false)
                    source.delete()
                }
            }
        }
        true
    }

    private fun getUniqueCollisionFile(dir: File, baseName: String): File {
        val dotIndex = baseName.lastIndexOf('.')
        val nameWithoutExt = if (dotIndex > 0) baseName.substring(0, dotIndex) else baseName
        val ext = if (dotIndex > 0) baseName.substring(dotIndex) else ""

        var count = 1
        var candidate: File
        do {
            candidate = File(dir, "$nameWithoutExt ($count)$ext")
            count++
        } while (candidate.exists())
        return candidate
    }

    suspend fun renameItem(oldPath: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        // illegal chars check
        val illegalChars = listOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        if (newName.isEmpty() || illegalChars.any { newName.contains(it) }) {
            return@withContext false
        }

        val file = File(oldPath)
        if (!file.exists()) return@withContext false

        val target = File(file.parentFile, newName)
        if (target.exists()) return@withContext false

        val success = file.renameTo(target)
        if (success && db.starredDao().isStarred(oldPath)) {
            val starred = db.starredDao()
            starred.unstarFile(oldPath)
            starred.starFile(
                StarredFileEntity(
                    filePath = target.absolutePath,
                    fileName = target.name,
                    fileSize = target.length(),
                    lastModified = target.lastModified(),
                    mimeType = getMimeType(target),
                    isDirectory = target.isDirectory
                )
            )
        }
        success
    }

    suspend fun batchRenameItems(renames: List<Pair<String, String>>): Int = withContext(Dispatchers.IO) {
        val illegalChars = listOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        var count = 0
        for ((oldPath, newName) in renames) {
            if (newName.isBlank() || illegalChars.any { newName.contains(it) }) continue
            val file = File(oldPath)
            if (!file.exists()) continue
            if (file.name == newName) continue

            var target = File(file.parentFile, newName)
            if (target.exists()) {
                target = getUniqueCollisionFile(file.parentFile, newName)
            }

            if (file.renameTo(target)) {
                count++
                if (db.starredDao().isStarred(oldPath)) {
                    val starred = db.starredDao()
                    starred.unstarFile(oldPath)
                    starred.starFile(
                        StarredFileEntity(
                            filePath = target.absolutePath,
                            fileName = target.name,
                            fileSize = target.length(),
                            lastModified = target.lastModified(),
                            mimeType = getMimeType(target),
                            isDirectory = target.isDirectory
                        )
                    )
                }
            }
        }
        count
    }

    suspend fun moveToBin(paths: List<String>): Boolean = withContext(Dispatchers.IO) {
        val binDir = File(context.filesDir, "ls_bin")
        if (!binDir.exists()) binDir.mkdirs()

        val timestamp = System.currentTimeMillis()

        for (path in paths) {
            val file = File(path)
            if (!file.exists()) continue

            val binTarget = File(binDir, "${timestamp}_${file.name}")
            val mimeType = getMimeType(file)
            val size = if (file.isDirectory) calculateFolderSize(file) else file.length()

            val success = file.renameTo(binTarget)
            if (!success) {
                if (file.isDirectory) {
                    file.copyRecursively(binTarget, overwrite = true)
                    file.deleteRecursively()
                } else {
                    file.copyTo(binTarget, overwrite = true)
                    file.delete()
                }
            }

            binDao.insertBinItem(
                BinItemEntity(
                    filePath = binTarget.absolutePath,
                    fileName = file.name,
                    originalPath = file.absolutePath,
                    fileSize = size,
                    mimeType = mimeType,
                    deletedTimestamp = timestamp
                )
            )
        }
        true
    }

    suspend fun restoreFromBin(filePath: String, customDestinationDir: String? = null): Boolean = withContext(Dispatchers.IO) {
        val binFile = File(filePath)
        val itemEntity = db.binDao().getBinItemByPath(filePath)
            ?: db.binDao().getExpiredBinItems(System.currentTimeMillis() + 86400000L * 100).find { it.filePath == filePath }
            ?: return@withContext false

        val targetParent = if (!customDestinationDir.isNullOrBlank()) {
            File(customDestinationDir)
        } else {
            val originalFile = File(itemEntity.originalPath)
            originalFile.parentFile ?: File(rootPath)
        }

        if (!targetParent.exists()) targetParent.mkdirs()

        val desiredName = itemEntity.fileName
        val targetFile = if (File(targetParent, desiredName).exists()) {
            getUniqueCollisionFile(targetParent, desiredName)
        } else {
            File(targetParent, desiredName)
        }

        val moved = binFile.renameTo(targetFile)
        if (moved) {
            binDao.deleteBinItem(filePath)
            true
        } else false
    }

    suspend fun deletePermanently(path: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(path)
        val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
        if (deleted) {
            binDao.deleteBinItem(path)
            starredDao.unstarFile(path)
        }
        deleted
    }

    suspend fun emptyBin(): Boolean = withContext(Dispatchers.IO) {
        val binDir = File(context.filesDir, "ls_bin")
        if (binDir.exists()) {
            binDir.deleteRecursively()
            binDir.mkdirs()
        }
        binDao.clearBin()
        true
    }

    suspend fun compressToZip(
        paths: List<String>,
        zipName: String,
        destDirPath: String,
        onProgress: ((bytesProcessed: Long, totalBytes: Long, currentFileName: String) -> Unit)? = null
    ): FileItem? = withContext(Dispatchers.IO) {
        val filesToZip = paths.map { File(it) }.filter { it.exists() }
        if (filesToZip.isEmpty()) return@withContext null

        val destDir = File(destDirPath)
        if (!destDir.exists()) destDir.mkdirs()

        val cleanName = if (zipName.endsWith(".zip", ignoreCase = true)) zipName else "$zipName.zip"
        val zipFile = File(destDir, cleanName)

        val totalBytes = calculateTotalSize(filesToZip)
        var bytesProcessed = 0L

        return@withContext try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { out ->
                for (file in filesToZip) {
                    bytesProcessed = addFileToZipWithProgress(
                        out = out,
                        file = file,
                        entryPath = file.name,
                        bytesProcessed = bytesProcessed,
                        totalBytes = totalBytes,
                        onProgress = onProgress
                    )
                }
            }
            mapToFileItem(zipFile)
        } catch (e: Exception) {
            e.printStackTrace()
            if (zipFile.exists()) {
                zipFile.delete()
            }
            null
        }
    }

    private fun calculateTotalSize(files: List<File>): Long {
        var total = 0L
        for (f in files) {
            if (f.isDirectory) {
                val children = f.listFiles() ?: continue
                total += calculateTotalSize(children.toList())
            } else {
                total += f.length()
            }
        }
        return total
    }

    private suspend fun addFileToZipWithProgress(
        out: ZipOutputStream,
        file: File,
        entryPath: String,
        bytesProcessed: Long,
        totalBytes: Long,
        onProgress: ((Long, Long, String) -> Unit)?
    ): Long {
        var currentBytes = bytesProcessed
        coroutineContext.ensureActive()

        if (file.isDirectory) {
            val children = file.listFiles() ?: return currentBytes
            for (child in children) {
                currentBytes = addFileToZipWithProgress(
                    out, child, "$entryPath/${child.name}", currentBytes, totalBytes, onProgress
                )
            }
        } else {
            onProgress?.invoke(currentBytes, totalBytes, file.name)
            FileInputStream(file).use { fi ->
                BufferedInputStream(fi).use { origin ->
                    val entry = ZipEntry(entryPath)
                    out.putNextEntry(entry)
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (origin.read(buffer).also { read = it } != -1) {
                        coroutineContext.ensureActive()
                        out.write(buffer, 0, read)
                        currentBytes += read
                        onProgress?.invoke(currentBytes, totalBytes, file.name)
                    }
                    out.closeEntry()
                }
            }
        }
        return currentBytes
    }

    suspend fun extractZip(
        zipPath: String,
        targetFolder: String,
        onProgress: ((bytesProcessed: Long, totalBytes: Long, currentFileName: String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val zipFile = File(zipPath)
        if (!zipFile.exists()) return@withContext false

        val destFolder = File(targetFolder)
        if (!destFolder.exists()) destFolder.mkdirs()

        val totalBytes = zipFile.length()
        var bytesProcessed = 0L

        return@withContext try {
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                val buffer = ByteArray(64 * 1024)
                while (entry != null) {
                    coroutineContext.ensureActive()
                    val newFile = File(destFolder, entry.name)

                    // Zip slip security check
                    if (!newFile.canonicalPath.startsWith(destFolder.canonicalPath)) {
                        throw SecurityException("Zip entry is outside target directory: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        newFile.parentFile?.mkdirs()
                        onProgress?.invoke(bytesProcessed, totalBytes, newFile.name)
                        FileOutputStream(newFile).use { fos ->
                            var read: Int
                            while (zis.read(buffer).also { read = it } != -1) {
                                coroutineContext.ensureActive()
                                fos.write(buffer, 0, read)
                                bytesProcessed += read
                                onProgress?.invoke(bytesProcessed, totalBytes, newFile.name)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun toggleStar(fileItem: FileItem): Boolean = withContext(Dispatchers.IO) {
        val isCurrentlyStarred = starredDao.isStarred(fileItem.path)
        if (isCurrentlyStarred) {
            starredDao.unstarFile(fileItem.path)
            false
        } else {
            starredDao.starFile(
                StarredFileEntity(
                    filePath = fileItem.path,
                    fileName = fileItem.name,
                    fileSize = fileItem.sizeBytes,
                    lastModified = fileItem.lastModified,
                    mimeType = fileItem.mimeType,
                    isDirectory = fileItem.isDirectory
                )
            )
            true
        }
    }

    suspend fun moveToSafeFolder(fileItem: FileItem): Boolean = withContext(Dispatchers.IO) {
        val safeDir = File(context.filesDir, "safe_folder")
        if (!safeDir.exists()) safeDir.mkdirs()

        val source = File(fileItem.path)
        if (!source.exists()) return@withContext false

        val safeTarget = File(safeDir, source.name)
        val success = source.renameTo(safeTarget)
        if (!success) {
            if (source.isDirectory) {
                source.copyRecursively(safeTarget, overwrite = true)
                source.deleteRecursively()
            } else {
                source.copyTo(safeTarget, overwrite = true)
                source.delete()
            }
        }
        true
    }

    suspend fun listSafeFolder(): List<FileItem> = withContext(Dispatchers.IO) {
        val safeDir = File(context.filesDir, "safe_folder")
        if (!safeDir.exists()) return@withContext emptyList()

        safeDir.listFiles()?.map { mapToFileItem(it) } ?: emptyList()
    }

    suspend fun removeFromSafeFolder(fileItem: FileItem): Boolean = withContext(Dispatchers.IO) {
        val source = File(fileItem.path)
        if (!source.exists()) return@withContext false

        val destDir = File(rootPath, "Documents")
        if (!destDir.exists()) destDir.mkdirs()

        val target = File(destDir, source.name)
        val success = source.renameTo(target)
        if (!success) {
            if (source.isDirectory) {
                source.copyRecursively(target, overwrite = true)
                source.deleteRecursively()
            } else {
                source.copyTo(target, overwrite = true)
                source.delete()
            }
        }
        true
    }

    suspend fun deleteFromSafeFolder(fileItem: FileItem): Boolean = withContext(Dispatchers.IO) {
        val source = File(fileItem.path)
        if (!source.exists()) return@withContext false
        if (source.isDirectory) {
            source.deleteRecursively()
        } else {
            source.delete()
        }
    }

    suspend fun scanDocumentSample(): FileItem? = withContext(Dispatchers.IO) {
        val docs = File(rootPath, "Documents")
        if (!docs.exists()) docs.mkdirs()

        val name = "Scanned_Doc_${System.currentTimeMillis()}.pdf"
        val pdfFile = File(docs, name)
        pdfFile.writeText("%PDF-1.5 LS Files Document Scanner - High Resolution Document Scan\nScanned Page 1")

        mapToFileItem(pdfFile)
    }

    suspend fun buildOrUpdateSmartSearchIndex(onProgress: ((String) -> Unit)? = null): Int = withContext(Dispatchers.IO) {
        val rootDir = File(rootPath)
        val allFiles = mutableListOf<File>()
        collectAllFiles(rootDir, allFiles, depth = 0, maxDepth = 20)

        var indexedCount = 0
        var ocrScannedCount = 0

        for (file in allFiles) {
            val category = determineCategory(file)
            val parentPath = file.parent ?: rootPath
            val mimeType = getMimeType(file)

            val existingIndex = fileIndexDao.getFileByPath(file.absolutePath)
            var ocrText = existingIndex?.ocrText

            val isImage = category == FileCategory.IMAGES || category == FileCategory.SCREENSHOTS || mimeType.startsWith("image/")
            if (isImage && ocrText.isNullOrBlank()) {
                onProgress?.invoke("OCR Scanning ${file.name} with Gemini AI...")
                ocrText = geminiOcrService.extractTextFromImageFile(file)
                if (!ocrText.isNullOrBlank()) {
                    ocrScannedCount++
                }
            }

            val indexedEntity = IndexedFileEntity(
                filePath = file.absolutePath,
                fileName = file.name,
                fileSize = if (file.isDirectory) calculateFolderSize(file) else file.length(),
                lastModified = file.lastModified(),
                mimeType = mimeType,
                categoryName = category.name,
                isDirectory = file.isDirectory,
                parentPath = parentPath,
                ocrText = ocrText,
                indexedTimestamp = System.currentTimeMillis()
            )
            fileIndexDao.insertFile(indexedEntity)
            indexedCount++
        }

        onProgress?.invoke("Indexed $indexedCount files ($ocrScannedCount image OCR scans updated)")
        indexedCount
    }

    private fun collectAllFiles(dir: File, results: MutableList<File>, depth: Int, maxDepth: Int) {
        if (depth > maxDepth) return
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.name.startsWith(".")) continue
            results.add(f)
            if (f.isDirectory) {
                if (dir.name.equals("Android", ignoreCase = true) && (f.name.equals("data", ignoreCase = true) || f.name.equals("obb", ignoreCase = true))) continue
                collectAllFiles(f, results, depth + 1, maxDepth)
            }
        }
    }

    private suspend fun mapIndexedToItem(entity: IndexedFileEntity): FileItem {
        val file = File(entity.filePath)
        val isStarred = db.starredDao().isStarred(entity.filePath)
        val category = try {
            FileCategory.valueOf(entity.categoryName)
        } catch (e: Exception) {
            FileCategory.OTHER
        }
        return FileItem(
            name = entity.fileName,
            path = entity.filePath,
            sizeBytes = entity.fileSize,
            lastModified = entity.lastModified,
            isDirectory = entity.isDirectory,
            mimeType = entity.mimeType,
            category = category,
            childCount = if (file.isDirectory) (file.listFiles()?.size ?: 0) else 0,
            isStarred = isStarred,
            isHidden = entity.fileName.startsWith("."),
            ocrText = entity.ocrText
        )
    }

    suspend fun searchFiles(query: String, smartSearch: Boolean = true): List<FileItem> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return@withContext emptyList()

        searchHistoryDao.insertQuery(SearchQueryEntity(trimmedQuery, System.currentTimeMillis()))

        if (smartSearch) {
            val indexedResults = fileIndexDao.searchFilesList(trimmedQuery)
            if (indexedResults.isNotEmpty()) {
                return@withContext indexedResults.map { mapIndexedToItem(it) }
            }
        }

        val results = mutableListOf<File>()
        val rootDir = File(rootPath)
        searchRecursive(rootDir, trimmedQuery.lowercase(), results, depth = 0, maxDepth = 20)

        val items = mutableListOf<FileItem>()
        for (f in results) {
            val fileItem = mapToFileItem(f)
            val isImage = fileItem.category == FileCategory.IMAGES || fileItem.category == FileCategory.SCREENSHOTS || fileItem.mimeType.startsWith("image/")
            if (smartSearch && isImage) {
                val ocr = geminiOcrService.extractTextFromImageFile(f)
                items.add(fileItem.copy(ocrText = ocr))
            } else {
                items.add(fileItem)
            }
        }
        items
    }

    private fun searchRecursive(dir: File, term: String, results: MutableList<File>, depth: Int, maxDepth: Int) {
        if (depth > maxDepth) return
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.name.startsWith(".")) continue
            if (f.name.lowercase().contains(term)) {
                results.add(f)
            }
            if (f.isDirectory) {
                if (dir.name.equals("Android", ignoreCase = true) && (f.name.equals("data", ignoreCase = true) || f.name.equals("obb", ignoreCase = true))) continue
                searchRecursive(f, term, results, depth + 1, maxDepth)
            }
        }
    }

    fun getRecentIndexedFilesFlow(limit: Int = 5): Flow<List<FileItem>> {
        return fileIndexDao.getRecentIndexedFilesFlow(limit).map { entities ->
            entities.map { mapIndexedToItem(it) }
        }
    }

    suspend fun getRecentIndexedFiles(limit: Int = 5): List<FileItem> = withContext(Dispatchers.IO) {
        var indexedEntities = try { fileIndexDao.getRecentIndexedFilesList(limit) } catch (e: Exception) { emptyList() }
        if (indexedEntities.isEmpty()) {
            autoSeedIndexForRecentFiles()
            indexedEntities = try { fileIndexDao.getRecentIndexedFilesList(limit) } catch (e: Exception) { emptyList() }
        }
        indexedEntities.map { mapIndexedToItem(it) }
    }

    private suspend fun autoSeedIndexForRecentFiles() {
        try {
            val diskRecent = getRecentFiles(10)
            val indexedList = diskRecent.map { item ->
                IndexedFileEntity(
                    filePath = item.path,
                    fileName = item.name,
                    fileSize = item.sizeBytes,
                    lastModified = item.lastModified,
                    mimeType = item.mimeType,
                    categoryName = item.category.name,
                    isDirectory = item.isDirectory,
                    parentPath = File(item.path).parent ?: rootPath,
                    ocrText = null,
                    indexedTimestamp = System.currentTimeMillis()
                )
            }
            if (indexedList.isNotEmpty()) {
                fileIndexDao.insertFiles(indexedList)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
