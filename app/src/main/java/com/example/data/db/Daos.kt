package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {
    @Query("SELECT * FROM files WHERE isDeleted = 0 ORDER BY isDirectory DESC, name ASC")
    fun getAllFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE isDeleted = 0 AND (path LIKE :directoryPath || '/%' AND path NOT LIKE :directoryPath || '/%/%') ORDER BY isDirectory DESC, name ASC")
    fun getFilesInDirectory(directoryPath: String): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE isDeleted = 1 ORDER BY lastModified DESC")
    fun getBinFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE isDeleted = 1 AND lastModified < :threshold")
    suspend fun getExpiredDeletedFiles(threshold: Long): List<FileEntity>

    @Query("DELETE FROM files WHERE isDeleted = 1 AND lastModified < :threshold")
    suspend fun deleteExpiredFiles(threshold: Long): Int

    @Query("SELECT * FROM files WHERE isDeleted = 0 AND name LIKE '%' || :query || '%' ORDER BY lastModified DESC")
    fun searchFilesByName(query: String): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE id = :id LIMIT 1")
    suspend fun getFileById(id: Long): FileEntity?

    @Query("SELECT * FROM files WHERE path = :path LIMIT 1")
    suspend fun getFileByPath(path: String): FileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: FileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: FileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<FileEntity>)

    @Update
    suspend fun update(file: FileEntity)

    @Update
    suspend fun updateFile(file: FileEntity)

    @Delete
    suspend fun delete(file: FileEntity)

    @Delete
    suspend fun deleteFile(file: FileEntity)

    @Query("DELETE FROM files WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM files WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Query("UPDATE files SET isDeleted = :isDeleted WHERE id = :id")
    suspend fun setDeleted(id: Long, isDeleted: Boolean)

    @Query("DELETE FROM files")
    suspend fun clearAll()
}

@Dao
interface FileIndexDao {
    @Query("SELECT * FROM indexed_files ORDER BY isDirectory DESC, fileName ASC")
    fun getAllIndexedFiles(): Flow<List<IndexedFileEntity>>

    @Query("SELECT * FROM indexed_files WHERE isDirectory = 0 ORDER BY lastModified DESC LIMIT :limit")
    fun getRecentIndexedFilesFlow(limit: Int = 5): Flow<List<IndexedFileEntity>>

    @Query("SELECT * FROM indexed_files WHERE isDirectory = 0 ORDER BY lastModified DESC LIMIT :limit")
    suspend fun getRecentIndexedFilesList(limit: Int = 5): List<IndexedFileEntity>

    @Query("SELECT * FROM indexed_files WHERE parentPath = :parentPath ORDER BY isDirectory DESC, fileName ASC")
    fun getFilesByParent(parentPath: String): Flow<List<IndexedFileEntity>>

    @Query("SELECT * FROM indexed_files WHERE categoryName = :category ORDER BY lastModified DESC")
    fun getFilesByCategory(category: String): Flow<List<IndexedFileEntity>>

    @Query("SELECT * FROM indexed_files WHERE fileName LIKE '%' || :query || '%' OR (ocrText IS NOT NULL AND ocrText LIKE '%' || :query || '%') ORDER BY lastModified DESC")
    fun searchFiles(query: String): Flow<List<IndexedFileEntity>>

    @Query("SELECT * FROM indexed_files WHERE fileName LIKE '%' || :query || '%' OR (ocrText IS NOT NULL AND ocrText LIKE '%' || :query || '%') ORDER BY lastModified DESC")
    suspend fun searchFilesList(query: String): List<IndexedFileEntity>

    @Query("UPDATE indexed_files SET ocrText = :ocrText WHERE filePath = :path")
    suspend fun updateOcrText(path: String, ocrText: String)

    @Query("SELECT * FROM indexed_files WHERE categoryName = 'IMAGES' AND (ocrText IS NULL OR ocrText = '')")
    suspend fun getUnindexedImages(): List<IndexedFileEntity>

    @Query("SELECT * FROM indexed_files WHERE filePath = :path LIMIT 1")
    suspend fun getFileByPath(path: String): IndexedFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: IndexedFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<IndexedFileEntity>)

    @Update
    suspend fun updateFile(file: IndexedFileEntity)

    @Delete
    suspend fun deleteFile(file: IndexedFileEntity)

    @Query("DELETE FROM indexed_files WHERE filePath = :path")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM indexed_files WHERE filePath LIKE :folderPath || '/%' OR filePath = :folderPath")
    suspend fun deleteFolderAndContents(folderPath: String)

    @Query("DELETE FROM indexed_files")
    suspend fun clearIndex()
}

@Dao
interface StarredDao {
    @Query("SELECT * FROM starred_files ORDER BY lastModified DESC")
    fun getAllStarred(): Flow<List<StarredFileEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM starred_files WHERE filePath = :path)")
    suspend fun isStarred(path: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun starFile(starred: StarredFileEntity)

    @Query("DELETE FROM starred_files WHERE filePath = :path")
    suspend fun unstarFile(path: String)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY tagName ASC")
    fun getAllTags(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: TagEntity): Long

    @Query("DELETE FROM tags WHERE tagId = :tagId")
    suspend fun deleteTag(tagId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTagToFile(crossRef: FileTagCrossRef)

    @Query("DELETE FROM file_tag_cross_ref WHERE filePath = :path AND tagId = :tagId")
    suspend fun removeTagFromFile(path: String, tagId: Long)

    @Query("SELECT tagId FROM file_tag_cross_ref WHERE filePath = :path")
    suspend fun getTagIdsForFile(path: String): List<Long>

    @Query("SELECT filePath FROM file_tag_cross_ref WHERE tagId = :tagId")
    suspend fun getFilePathsForTag(tagId: Long): List<String>
}

@Dao
interface BinDao {
    @Query("SELECT * FROM bin_items ORDER BY deletedTimestamp DESC")
    fun getAllBinItems(): Flow<List<BinItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBinItem(item: BinItemEntity)

    @Query("DELETE FROM bin_items WHERE filePath = :filePath")
    suspend fun deleteBinItem(filePath: String)

    @Query("DELETE FROM bin_items")
    suspend fun clearBin()

    @Query("SELECT * FROM bin_items WHERE filePath = :filePath LIMIT 1")
    suspend fun getBinItemByPath(filePath: String): BinItemEntity?

    @Query("SELECT * FROM bin_items WHERE deletedTimestamp < :threshold")
    suspend fun getExpiredBinItems(threshold: Long): List<BinItemEntity>
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 20")
    fun getRecentQueries(): Flow<List<SearchQueryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuery(query: SearchQueryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteQuery(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearHistory()
}

@Dao
interface CloudDao {
    @Query("SELECT * FROM cloud_accounts ORDER BY connectedTimestamp DESC")
    fun getAllAccounts(): Flow<List<CloudAccountEntity>>

    @Query("SELECT * FROM cloud_accounts WHERE accountId = :id LIMIT 1")
    suspend fun getAccountById(id: String): CloudAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: CloudAccountEntity)

    @Query("DELETE FROM cloud_accounts WHERE accountId = :id")
    suspend fun deleteAccount(id: String)
}

@Dao
interface SettingsDao {
    @Query("SELECT value FROM app_settings WHERE key = :key")
    suspend fun getSetting(key: String): String?

    @Query("SELECT * FROM app_settings")
    fun getAllSettings(): Flow<List<AppSettingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: AppSettingEntity)
}
