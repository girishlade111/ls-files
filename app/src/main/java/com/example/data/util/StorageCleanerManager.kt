package com.example.data.util

import android.os.Environment
import com.example.data.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class JunkItem(
    val file: File,
    val category: JunkCategory,
    val size: Long
)

enum class JunkCategory {
    CACHE, TEMP_FILES, LOG_FILES, APK_INSTALLERS, EMPTY_FOLDERS
}

data class DuplicateGroup(
    val hash: String,
    val fileSize: Long,
    val files: List<FileItem>
)

class StorageCleanerManager {

    suspend fun scanJunkFiles(rootPath: String = Environment.getExternalStorageDirectory().absolutePath): List<JunkItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<JunkItem>()
        val root = File(rootPath)
        if (!root.exists() || !root.canRead()) return@withContext result

        fun traverse(dir: File) {
            val children = dir.listFiles() ?: return
            if (children.isEmpty() && dir.absolutePath != rootPath) {
                result.add(JunkItem(dir, JunkCategory.EMPTY_FOLDERS, 0L))
                return
            }

            for (file in children) {
                if (file.name.startsWith(".")) continue
                if (file.isDirectory) {
                    if (file.name.equals("cache", ignoreCase = true) || file.name.equals(".cache", ignoreCase = true)) {
                        val folderSize = calculateDirectorySize(file)
                        result.add(JunkItem(file, JunkCategory.CACHE, folderSize))
                    } else {
                        traverse(file)
                    }
                } else {
                    val ext = file.extension.lowercase()
                    val name = file.name.lowercase()
                    when {
                        ext == "tmp" || ext == "temp" || ext == "bak" || name.contains("temp") -> {
                            result.add(JunkItem(file, JunkCategory.TEMP_FILES, file.length()))
                        }
                        ext == "log" -> {
                            result.add(JunkItem(file, JunkCategory.LOG_FILES, file.length()))
                        }
                        ext == "apk" -> {
                            result.add(JunkItem(file, JunkCategory.APK_INSTALLERS, file.length()))
                        }
                    }
                }
            }
        }

        try {
            traverse(root)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result
    }

    suspend fun scanDuplicateFiles(rootPath: String = Environment.getExternalStorageDirectory().absolutePath): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val sizeMap = mutableMapOf<Long, MutableList<File>>()
        val root = File(rootPath)
        if (!root.exists() || !root.canRead()) return@withContext emptyList()

        fun collectFiles(dir: File) {
            val children = dir.listFiles() ?: return
            for (file in children) {
                if (file.name.startsWith(".")) continue
                if (file.isDirectory) {
                    if (file.name.equals("Android", ignoreCase = true) && dir.absolutePath == rootPath) continue
                    collectFiles(file)
                } else {
                    val len = file.length()
                    if (len > 1024 * 10) { // Only check files > 10KB
                        sizeMap.getOrPut(len) { mutableListOf() }.add(file)
                    }
                }
            }
        }

        try {
            collectFiles(root)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val candidates = sizeMap.filter { it.value.size > 1 }
        val duplicateGroups = mutableListOf<DuplicateGroup>()

        for ((size, files) in candidates) {
            val hashGroups = mutableMapOf<String, MutableList<File>>()
            for (f in files) {
                val hash = calculateFastHash(f)
                if (hash != null) {
                    hashGroups.getOrPut(hash) { mutableListOf() }.add(f)
                }
            }

            for ((hash, matchingFiles) in hashGroups) {
                if (matchingFiles.size > 1) {
                    val fileItems = matchingFiles.map { f ->
                        FileItem(
                            name = f.name,
                            path = f.absolutePath,
                            sizeBytes = f.length(),
                            lastModified = f.lastModified(),
                            isDirectory = false
                        )
                    }
                    duplicateGroups.add(DuplicateGroup(hash, size, fileItems))
                }
            }
        }

        duplicateGroups.sortedByDescending { it.fileSize * it.files.size }
    }

    suspend fun scanLargeFiles(
        rootPath: String = Environment.getExternalStorageDirectory().absolutePath,
        minSizeMB: Long = 50
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val minSizeBytes = minSizeMB * 1024 * 1024
        val result = mutableListOf<FileItem>()
        val root = File(rootPath)
        if (!root.exists() || !root.canRead()) return@withContext result

        fun traverse(dir: File) {
            val children = dir.listFiles() ?: return
            for (file in children) {
                if (file.name.startsWith(".")) continue
                if (file.isDirectory) {
                    if (file.name.equals("Android", ignoreCase = true) && dir.absolutePath == rootPath) continue
                    traverse(file)
                } else {
                    if (file.length() >= minSizeBytes) {
                        result.add(
                            FileItem(
                                name = file.name,
                                path = file.absolutePath,
                                sizeBytes = file.length(),
                                lastModified = file.lastModified(),
                                isDirectory = false
                            )
                        )
                    }
                }
            }
        }

        try {
            traverse(root)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        result.sortedByDescending { it.sizeBytes }
    }

    suspend fun deleteFiles(files: List<File>): Long = withContext(Dispatchers.IO) {
        var reclaimedBytes = 0L
        for (f in files) {
            try {
                val len = if (f.isDirectory) calculateDirectorySize(f) else f.length()
                if (f.deleteRecursively()) {
                    reclaimedBytes += len
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        reclaimedBytes
    }

    private fun calculateDirectorySize(dir: File): Long {
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (f in files) {
            size += if (f.isDirectory) calculateDirectorySize(f) else f.length()
        }
        return size
    }

    private fun calculateFastHash(file: File): String? {
        return try {
            val len = file.length()
            if (len <= 0) return null
            val md = MessageDigest.getInstance("SHA-256")
            md.update(len.toString().toByteArray(Charsets.UTF_8))

            java.io.RandomAccessFile(file, "r").use { raf ->
                val bufferSize = 8192
                // 1. Read Head (first 8KB)
                val headBuffer = ByteArray(bufferSize.coerceAtMost(len.toInt()))
                val headRead = raf.read(headBuffer)
                if (headRead > 0) md.update(headBuffer, 0, headRead)

                // 2. Read Middle (8KB at 50% offset) if file > 16KB
                if (len > bufferSize * 2) {
                    raf.seek(len / 2)
                    val midBuffer = ByteArray(bufferSize)
                    val midRead = raf.read(midBuffer)
                    if (midRead > 0) md.update(midBuffer, 0, midRead)
                }

                // 3. Read Tail (last 8KB) if file > 32KB
                if (len > bufferSize * 4) {
                    raf.seek(len - bufferSize)
                    val tailBuffer = ByteArray(bufferSize)
                    val tailRead = raf.read(tailBuffer)
                    if (tailRead > 0) md.update(tailBuffer, 0, tailRead)
                }
            }
            val digest = md.digest()
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
}
