package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.cloud.*
import com.example.data.db.AppDatabase
import com.example.data.db.*
import com.example.data.model.*
import com.example.data.repository.FileRepository
import com.example.ui.util.HapticType
import com.example.ui.components.ZipProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

enum class NavDestination {
    HOME, BROWSE, TAGS, SEARCH, RECENT, BIN, SETTINGS, SAFE_FOLDER
}

sealed class UIEvent {
    data class ShowSnackbar(val message: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null) : UIEvent()
    data class TriggerHaptic(val type: HapticType) : UIEvent()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository: FileRepository
    private val _db = AppDatabase.getDatabase(application)

    init {
        repository = FileRepository(application, _db)
        viewModelScope.launch {
            try {
                repository.seedDefaultTagsIfEmpty()
                refreshStorageInfo()
                loadDirectory(repository.rootPath)
                refreshCategories()
                refreshRecentFiles()
                refreshRecentIndexedFiles()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        viewModelScope.launch {
            try {
                val storedPin = repository.settingsDao.getSetting("safe_folder_pin")
                if (!storedPin.isNullOrEmpty()) {
                    _safeFolderPin.value = storedPin
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        viewModelScope.launch {
            try {
                repository.getRecentIndexedFilesFlow(5).collect { list ->
                    if (list.isNotEmpty()) {
                        _recentIndexedFiles.value = list
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Navigation state
    private val _currentDestination = MutableStateFlow(NavDestination.HOME)
    val currentDestination: StateFlow<NavDestination> = _currentDestination.asStateFlow()

    fun navigateTo(destination: NavDestination) {
        _currentDestination.value = destination
        clearSelection()
    }

    // Current Folder Path & Items in Browse Screen
    private val _currentDirectoryPath = MutableStateFlow(repository.rootPath)
    val currentDirectoryPath: StateFlow<String> = _currentDirectoryPath.asStateFlow()

    private val _directoryFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val directoryFiles: StateFlow<List<FileItem>> = _directoryFiles.asStateFlow()

    private val _isLoadingDirectory = MutableStateFlow(false)
    val isLoadingDirectory: StateFlow<Boolean> = _isLoadingDirectory.asStateFlow()

    // ZIP Operation Progress State & Job
    private val _zipProgress = MutableStateFlow<ZipProgress?>(null)
    val zipProgress: StateFlow<ZipProgress?> = _zipProgress.asStateFlow()

    private var activeZipJob: Job? = null

    // View & Sort Settings
    private val prefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _viewMode = MutableStateFlow(ViewMode.LIST)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    fun toggleViewMode() {
        _viewMode.value = if (_viewMode.value == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
    }

    private val _sortField = MutableStateFlow(
        runCatching {
            val savedName = prefs.getString("sort_field", SortField.NAME.name)
            SortField.valueOf(savedName ?: SortField.NAME.name)
        }.getOrDefault(SortField.NAME)
    )
    val sortField: StateFlow<SortField> = _sortField.asStateFlow()

    private val _sortOrder = MutableStateFlow(
        runCatching {
            val savedOrder = prefs.getString("sort_order", SortOrder.ASCENDING.name)
            SortOrder.valueOf(savedOrder ?: SortOrder.ASCENDING.name)
        }.getOrDefault(SortOrder.ASCENDING)
    )
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private fun saveSortPreferences() {
        prefs.edit()
            .putString("sort_field", _sortField.value.name)
            .putString("sort_order", _sortOrder.value.name)
            .apply()
    }

    fun setSortField(field: SortField) {
        _sortField.value = field
        saveSortPreferences()
        applySortAndFilter()
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        saveSortPreferences()
        applySortAndFilter()
    }

    fun toggleSortOrder() {
        _sortOrder.value = if (_sortOrder.value == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING
        saveSortPreferences()
        applySortAndFilter()
    }

    fun setSortOption(field: SortField, order: SortOrder) {
        _sortField.value = field
        _sortOrder.value = order
        saveSortPreferences()
        applySortAndFilter()
    }

    fun setSort(field: SortField) {
        if (_sortField.value == field) {
            _sortOrder.value = if (_sortOrder.value == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING
        } else {
            _sortField.value = field
        }
        saveSortPreferences()
        applySortAndFilter()
    }

    // Selection mode state
    private val _selectedPaths = MutableStateFlow<Set<String>>(emptySet())
    val selectedPaths: StateFlow<Set<String>> = _selectedPaths.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedPaths.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setSelectedPaths(paths: Set<String>) {
        _selectedPaths.value = paths
    }

    fun toggleSelection(path: String) {
        val current = _selectedPaths.value.toMutableSet()
        if (current.contains(path)) {
            current.remove(path)
        } else {
            current.add(path)
        }
        _selectedPaths.value = current
    }

    fun selectAll() {
        val allPaths = _directoryFiles.value.map { it.path }.toSet()
        _selectedPaths.value = allPaths
    }

    fun clearSelection() {
        _selectedPaths.value = emptySet()
    }

    // Storage info & Categories
    private val _storageSpaceInfo = MutableStateFlow(StorageSpaceInfo(0L, 0L, 0L))
    val storageSpaceInfo: StateFlow<StorageSpaceInfo> = _storageSpaceInfo.asStateFlow()

    private val _categorySizes = MutableStateFlow<Map<FileCategory, Long>>(emptyMap())
    val categorySizes: StateFlow<Map<FileCategory, Long>> = _categorySizes.asStateFlow()

    private val _recentFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val recentFiles: StateFlow<List<FileItem>> = _recentFiles.asStateFlow()

    private val _recentIndexedFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val recentIndexedFiles: StateFlow<List<FileItem>> = _recentIndexedFiles.asStateFlow()

    // Selected Category View state
    private val _selectedCategory = MutableStateFlow<FileCategory?>(null)
    val selectedCategory: StateFlow<FileCategory?> = _selectedCategory.asStateFlow()

    private val _categoryFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val categoryFiles: StateFlow<List<FileItem>> = _categoryFiles.asStateFlow()

    fun openCategory(category: FileCategory) {
        _selectedCategory.value = category
        viewModelScope.launch {
            _isLoadingDirectory.value = true
            _categoryFiles.value = repository.getCategoryFiles(category)
            _isLoadingDirectory.value = false
        }
    }

    fun closeCategory() {
        _selectedCategory.value = null
    }

    // UI Events & Snackbars
    private val _uiEvents = MutableSharedFlow<UIEvent>()
    val uiEvents = _uiEvents.asSharedFlow()

    fun emitSnackbar(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        viewModelScope.launch {
            _uiEvents.emit(UIEvent.ShowSnackbar(message, actionLabel, onAction))
        }
    }

    fun emitHaptic(type: HapticType) {
        viewModelScope.launch {
            _uiEvents.emit(UIEvent.TriggerHaptic(type))
        }
    }

    fun refreshStorageInfo() {
        viewModelScope.launch {
            try {
                _storageSpaceInfo.value = repository.getStorageSpaceInfo()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun refreshCategories() {
        viewModelScope.launch {
            try {
                _categorySizes.value = repository.getCategorySizes()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun refreshRecentFiles() {
        viewModelScope.launch {
            try {
                _recentFiles.value = repository.getRecentFiles(20)
                refreshRecentIndexedFiles()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun refreshRecentIndexedFiles() {
        viewModelScope.launch {
            try {
                _recentIndexedFiles.value = repository.getRecentIndexedFiles(5)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadDirectory(path: String) {
        _currentDirectoryPath.value = path
        viewModelScope.launch {
            _isLoadingDirectory.value = true
            try {
                val rawFiles = repository.listFilesAt(path, _showHiddenFiles.value)
                _directoryFiles.value = rawFiles
                applySortAndFilter()
            } catch (e: Exception) {
                e.printStackTrace()
                _directoryFiles.value = emptyList()
            } finally {
                _isLoadingDirectory.value = false
            }
        }
    }

    fun navigateUpDirectory() {
        val currentFile = File(_currentDirectoryPath.value)
        val parent = currentFile.parent
        if (parent != null && currentFile.absolutePath != repository.rootPath) {
            loadDirectory(parent)
        }
    }

    private fun applySortAndFilter() {
        val files = _directoryFiles.value.toMutableList()
        val isAsc = _sortOrder.value == SortOrder.ASCENDING
        files.sortWith { f1, f2 ->
            if (f1.isDirectory && !f2.isDirectory) return@sortWith -1
            if (!f1.isDirectory && f2.isDirectory) return@sortWith 1
            val res = when (_sortField.value) {
                SortField.NAME -> f1.name.compareTo(f2.name, ignoreCase = true)
                SortField.DATE -> f1.lastModified.compareTo(f2.lastModified)
                SortField.SIZE -> f1.sizeBytes.compareTo(f2.sizeBytes)
                SortField.TYPE -> f1.mimeType.compareTo(f2.mimeType, ignoreCase = true)
            }
            if (isAsc) res else -res
        }
        _directoryFiles.value = files
    }

    // File Operations API
    fun performCopySelected(destinationDirPath: String) {
        val targets = _selectedPaths.value.toList()
        viewModelScope.launch {
            val count = targets.size
            val success = repository.copyItems(targets, destinationDirPath)
            if (success) {
                clearSelection()
                loadDirectory(_currentDirectoryPath.value)
                refreshStorageInfo()
                emitHaptic(HapticType.MOVE_SUCCESS)
                emitSnackbar("Copied $count item(s)")
            }
        }
    }

    fun performMoveSelected(destinationDirPath: String) {
        val targets = _selectedPaths.value.toList()
        viewModelScope.launch {
            val count = targets.size
            val success = repository.moveItems(targets, destinationDirPath)
            if (success) {
                clearSelection()
                loadDirectory(_currentDirectoryPath.value)
                refreshStorageInfo()
                emitHaptic(HapticType.MOVE_SUCCESS)
                emitSnackbar("Moved $count item(s)")
            }
        }
    }

    fun performRenameSelected(targetPath: String, newName: String) {
        viewModelScope.launch {
            val success = repository.renameItem(targetPath, newName)
            if (success) {
                clearSelection()
                loadDirectory(_currentDirectoryPath.value)
                refreshRecentFiles()
                refreshCategories()
                emitHaptic(HapticType.MOVE_SUCCESS)
                emitSnackbar("Renamed to $newName")
            } else {
                emitSnackbar("Failed to rename file")
            }
        }
    }

    fun performBatchRename(renames: List<Pair<String, String>>) {
        viewModelScope.launch {
            val successCount = repository.batchRenameItems(renames)
            clearSelection()
            loadDirectory(_currentDirectoryPath.value)
            refreshRecentFiles()
            refreshCategories()
            emitHaptic(HapticType.MOVE_SUCCESS)
            emitSnackbar("Batch renamed $successCount item(s)")
        }
    }

    fun performDeleteSelected() {
        val targets = _selectedPaths.value.toList()
        viewModelScope.launch {
            val count = targets.size
            val success = repository.moveToBin(targets)
            if (success) {
                clearSelection()
                loadDirectory(_currentDirectoryPath.value)
                refreshStorageInfo()
                refreshRecentFiles()
                emitHaptic(HapticType.DELETE)
                emitSnackbar("Deleted $count item(s) · Moved to Bin")
            }
        }
    }

    fun cancelZipOperation() {
        activeZipJob?.cancel()
        activeZipJob = null
        _zipProgress.value = null
        emitHaptic(HapticType.SELECTION_TOGGLE)
        emitSnackbar("ZIP operation cancelled")
    }

    fun performCompressSelected(zipName: String) {
        val targets = _selectedPaths.value.toList()
        if (targets.isEmpty()) return

        activeZipJob?.cancel()
        activeZipJob = viewModelScope.launch {
            _zipProgress.value = ZipProgress(
                isCompressing = true,
                title = "Compressing to ${zipName}.zip",
                currentFileName = "Preparing...",
                bytesProcessed = 0L,
                totalBytes = 0L
            )

            try {
                val zipFile = repository.compressToZip(
                    paths = targets,
                    zipName = zipName,
                    destDirPath = _currentDirectoryPath.value,
                    onProgress = { bytes, total, fileName ->
                        _zipProgress.value = ZipProgress(
                            isCompressing = true,
                            title = "Compressing to ${zipName}.zip",
                            currentFileName = fileName,
                            bytesProcessed = bytes,
                            totalBytes = total
                        )
                    }
                )

                _zipProgress.value = null
                if (zipFile != null) {
                    clearSelection()
                    loadDirectory(_currentDirectoryPath.value)
                    refreshStorageInfo()
                    refreshCategories()
                    emitHaptic(HapticType.MOVE_SUCCESS)
                    emitSnackbar("Compressed to ${zipFile.name}")
                } else {
                    emitSnackbar("Compression failed")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _zipProgress.value = null
                emitSnackbar("Compression cancelled")
                throw e
            } catch (e: Exception) {
                _zipProgress.value = null
                emitSnackbar("Compression error: ${e.localizedMessage ?: "Unknown error"}")
            } finally {
                activeZipJob = null
            }
        }
    }

    fun performExtractZip(zipPath: String) {
        val file = File(zipPath)
        if (!file.exists()) return

        activeZipJob?.cancel()
        activeZipJob = viewModelScope.launch {
            val fileName = file.name
            _zipProgress.value = ZipProgress(
                isCompressing = false,
                title = "Extracting $fileName",
                currentFileName = "Preparing...",
                bytesProcessed = 0L,
                totalBytes = file.length()
            )

            try {
                val parent = file.parent ?: repository.rootPath
                val success = repository.extractZip(
                    zipPath = zipPath,
                    targetFolder = parent,
                    onProgress = { bytes, total, itemFileName ->
                        _zipProgress.value = ZipProgress(
                            isCompressing = false,
                            title = "Extracting $fileName",
                            currentFileName = itemFileName,
                            bytesProcessed = bytes,
                            totalBytes = total
                        )
                    }
                )

                _zipProgress.value = null
                if (success) {
                    loadDirectory(_currentDirectoryPath.value)
                    refreshStorageInfo()
                    refreshCategories()
                    emitHaptic(HapticType.MOVE_SUCCESS)
                    emitSnackbar("Extracted to folder")
                } else {
                    emitSnackbar("Extraction failed")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _zipProgress.value = null
                emitSnackbar("Extraction cancelled")
                throw e
            } catch (e: Exception) {
                _zipProgress.value = null
                emitSnackbar("Extraction error: ${e.localizedMessage ?: "Unknown error"}")
            } finally {
                activeZipJob = null
            }
        }
    }

    fun performToggleStar(fileItem: FileItem) {
        viewModelScope.launch {
            val starred = repository.toggleStar(fileItem)
            loadDirectory(_currentDirectoryPath.value)
            emitSnackbar(if (starred) "Added to Starred" else "Removed from Starred")
        }
    }

    fun performMoveToSafeFolder(fileItem: FileItem) {
        viewModelScope.launch {
            val success = repository.moveToSafeFolder(fileItem)
            if (success) {
                loadDirectory(_currentDirectoryPath.value)
                refreshRecentFiles()
                emitHaptic(HapticType.MOVE_SUCCESS)
                emitSnackbar("Moved to Safe folder")
            }
        }
    }

    fun performScanDocument() {
        viewModelScope.launch {
            val scannedDoc = repository.scanDocumentSample()
            if (scannedDoc != null) {
                refreshRecentFiles()
                refreshCategories()
                emitSnackbar("Document scanned and saved to Documents")
            }
        }
    }

    // Room Persistent Flows
    val starredFilesFlow: Flow<List<StarredFileEntity>> = repository.starredDao.getAllStarred()
    val binItemsFlow: Flow<List<BinItemEntity>> = repository.binDao.getAllBinItems()
    val tagsFlow: Flow<List<TagEntity>> = repository.tagDao.getAllTags()
    val searchHistoryFlow: Flow<List<SearchQueryEntity>> = repository.searchHistoryDao.getRecentQueries()
    val cloudAccountsFlow: Flow<List<CloudAccountEntity>> = repository.cloudDao.getAllAccounts()

    // Bin actions
    fun restoreBinItem(filePath: String, customDestinationDir: String? = null) {
        viewModelScope.launch {
            val ok = repository.restoreFromBin(filePath, customDestinationDir)
            if (ok) {
                loadDirectory(_currentDirectoryPath.value)
                refreshRecentFiles()
                refreshCategories()
                refreshStorageInfo()
                emitHaptic(HapticType.MOVE_SUCCESS)
                emitSnackbar("Restored from Bin")
            } else {
                emitSnackbar("Failed to restore item")
            }
        }
    }

    fun restoreBinItems(filePaths: List<String>, customDestinationDir: String? = null) {
        viewModelScope.launch {
            var restoredCount = 0
            for (path in filePaths) {
                if (repository.restoreFromBin(path, customDestinationDir)) {
                    restoredCount++
                }
            }
            if (restoredCount > 0) {
                loadDirectory(_currentDirectoryPath.value)
                refreshRecentFiles()
                refreshCategories()
                refreshStorageInfo()
                emitHaptic(HapticType.MOVE_SUCCESS)
                emitSnackbar("Restored $restoredCount item(s)")
            } else {
                emitSnackbar("Failed to restore item(s)")
            }
        }
    }

    fun deleteBinPermanently(filePath: String) {
        viewModelScope.launch {
            repository.deletePermanently(filePath)
            emitHaptic(HapticType.DELETE)
            emitSnackbar("Permanently deleted item")
        }
    }

    fun deleteFolderPermanently(filePath: String) {
        viewModelScope.launch {
            repository.deletePermanently(filePath)
            loadDirectory(_currentDirectoryPath.value)
            emitHaptic(HapticType.DELETE)
            emitSnackbar("Folder permanently deleted")
        }
    }

    fun emptyBin() {
        viewModelScope.launch {
            repository.emptyBin()
            emitHaptic(HapticType.DELETE)
            emitSnackbar("Bin emptied")
        }
    }

    // Tags actions
    fun createTag(name: String, colorHex: String) {
        viewModelScope.launch {
            repository.tagDao.insertTag(TagEntity(tagName = name, tagColorHex = colorHex))
            emitSnackbar("Tag '$name' created")
        }
    }

    fun addTagToFiles(paths: Set<String>, tagId: Long, tagName: String) {
        viewModelScope.launch {
            try {
                paths.forEach { path ->
                    repository.tagDao.addTagToFile(FileTagCrossRef(path, tagId))
                }
                clearSelection()
                loadDirectory(_currentDirectoryPath.value)
                refreshRecentFiles()
                emitSnackbar("Tagged ${paths.size} item(s) with '$tagName'")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Search logic
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<FileItem>>(emptyList())
    val searchResults: StateFlow<List<FileItem>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isIndexingOcr = MutableStateFlow(false)
    val isIndexingOcr: StateFlow<Boolean> = _isIndexingOcr.asStateFlow()

    private val _indexingStatus = MutableStateFlow<String?>(null)
    val indexingStatus: StateFlow<String?> = _indexingStatus.asStateFlow()

    fun triggerOcrSmartSearchIndexing() {
        if (_isIndexingOcr.value) return
        viewModelScope.launch {
            _isIndexingOcr.value = true
            _indexingStatus.value = "Starting Gemini AI OCR indexing..."
            try {
                val count = repository.buildOrUpdateSmartSearchIndex { status ->
                    _indexingStatus.value = status
                }
                emitSnackbar("Smart search indexed $count items with Gemini OCR")
            } catch (e: Exception) {
                emitSnackbar("OCR Indexing completed")
            } finally {
                _isIndexingOcr.value = false
                _indexingStatus.value = null
                if (_searchQuery.value.isNotBlank()) {
                    updateSearchQuery(_searchQuery.value)
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.trim().isEmpty()) {
            _searchResults.value = emptyList()
        } else {
            viewModelScope.launch {
                _isSearching.value = true
                try {
                    _searchResults.value = repository.searchFiles(query, _smartSearchEnabled.value)
                } catch (e: Exception) {
                    e.printStackTrace()
                    _searchResults.value = emptyList()
                } finally {
                    _isSearching.value = false
                }
            }
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            repository.searchHistoryDao.clearHistory()
            emitSnackbar("Search history cleared")
        }
    }

    fun deleteSearchQuery(query: String) {
        viewModelScope.launch {
            repository.searchHistoryDao.deleteQuery(query)
        }
    }

    // Cloud actions
    fun connectCloudAccount(type: CloudProviderType) {
        viewModelScope.launch {
            val adapter: CloudProviderAdapter = when (type) {
                CloudProviderType.GOOGLE_DRIVE -> GoogleDriveAdapter()
                CloudProviderType.ONEDRIVE -> OneDriveAdapter()
                CloudProviderType.DROPBOX -> DropboxAdapter()
            }
            val account = adapter.connectAccount()
            repository.cloudDao.insertAccount(
                CloudAccountEntity(
                    accountId = account.accountId,
                    providerType = account.providerType.name,
                    accountName = account.displayName,
                    accountEmail = account.email,
                    connectedTimestamp = System.currentTimeMillis()
                )
            )
            emitSnackbar("Connected to ${type.displayName}")
        }
    }

    fun disconnectCloudAccount(accountId: String) {
        viewModelScope.launch {
            val accountEntity = repository.cloudDao.getAccountById(accountId)
            if (accountEntity != null) {
                val providerType = try {
                    CloudProviderType.valueOf(accountEntity.providerType)
                } catch (e: Exception) {
                    CloudProviderType.GOOGLE_DRIVE
                }
                val adapter: CloudProviderAdapter = when (providerType) {
                    CloudProviderType.GOOGLE_DRIVE -> com.example.data.cloud.GoogleDriveCloudProvider()
                    CloudProviderType.ONEDRIVE -> OneDriveAdapter()
                    CloudProviderType.DROPBOX -> DropboxAdapter()
                }
                // Revoke OAuth credentials & clear adapter session
                adapter.disconnect()
                // Clear provider row from local database (which updates Home screen storage section)
                repository.cloudDao.deleteAccount(accountId)
                emitSnackbar("Disconnected ${accountEntity.accountName} and revoked OAuth access")
            } else {
                repository.cloudDao.deleteAccount(accountId)
                emitSnackbar("Cloud account disconnected")
            }
        }
    }

    // Settings state
    private val _showHiddenFiles = MutableStateFlow(false)
    val showHiddenFiles: StateFlow<Boolean> = _showHiddenFiles.asStateFlow()

    fun toggleShowHiddenFiles(enabled: Boolean) {
        _showHiddenFiles.value = enabled
        loadDirectory(_currentDirectoryPath.value)
    }

    private val _smartSearchEnabled = MutableStateFlow(true)
    val smartSearchEnabled: StateFlow<Boolean> = _smartSearchEnabled.asStateFlow()

    fun toggleSmartSearch(enabled: Boolean) {
        _smartSearchEnabled.value = enabled
    }

    private val _pauseSearchHistory = MutableStateFlow(false)
    val pauseSearchHistory: StateFlow<Boolean> = _pauseSearchHistory.asStateFlow()

    fun togglePauseSearchHistory(enabled: Boolean) {
        _pauseSearchHistory.value = enabled
    }

    // Safe Folder State
    private val _safeFolderPin = MutableStateFlow<String?>(null)
    val safeFolderPin: StateFlow<String?> = _safeFolderPin.asStateFlow()

    private val _isSafeFolderUnlocked = MutableStateFlow(false)
    val isSafeFolderUnlocked: StateFlow<Boolean> = _isSafeFolderUnlocked.asStateFlow()

    private val _safeFolderFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val safeFolderFiles: StateFlow<List<FileItem>> = _safeFolderFiles.asStateFlow()

    fun setSafeFolderPin(pin: String) {
        _safeFolderPin.value = pin
        _isSafeFolderUnlocked.value = true
        viewModelScope.launch {
            try {
                repository.settingsDao.setSetting(AppSettingEntity("safe_folder_pin", pin))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        loadSafeFolderFiles()
        emitSnackbar("Safe Folder PIN created")
    }

    fun unlockSafeFolder(pin: String): Boolean {
        if (_safeFolderPin.value == pin || _safeFolderPin.value == null) {
            if (_safeFolderPin.value == null) {
                _safeFolderPin.value = pin
                viewModelScope.launch {
                    try {
                        repository.settingsDao.setSetting(AppSettingEntity("safe_folder_pin", pin))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            _isSafeFolderUnlocked.value = true
            loadSafeFolderFiles()
            return true
        }
        return false
    }

    fun unlockSafeFolderWithBiometric() {
        _isSafeFolderUnlocked.value = true
        loadSafeFolderFiles()
        emitSnackbar("Unlocked via Biometric authentication")
    }

    fun lockSafeFolder() {
        _isSafeFolderUnlocked.value = false
        _safeFolderFiles.value = emptyList()
    }

    fun loadSafeFolderFiles() {
        viewModelScope.launch {
            _safeFolderFiles.value = repository.listSafeFolder()
        }
    }

    fun performRemoveFromSafeFolder(fileItem: FileItem) {
        viewModelScope.launch {
            val success = repository.removeFromSafeFolder(fileItem)
            if (success) {
                loadSafeFolderFiles()
                loadDirectory(_currentDirectoryPath.value)
                refreshRecentFiles()
                refreshCategories()
                emitHaptic(HapticType.MOVE_SUCCESS)
                emitSnackbar("Restored '${fileItem.name}' to Documents")
            } else {
                emitSnackbar("Failed to restore file")
            }
        }
    }

    fun performDeleteFromSafeFolder(fileItem: FileItem) {
        viewModelScope.launch {
            val success = repository.deleteFromSafeFolder(fileItem)
            if (success) {
                loadSafeFolderFiles()
                emitHaptic(HapticType.DELETE)
                emitSnackbar("Permanently deleted '${fileItem.name}'")
            } else {
                emitSnackbar("Failed to delete file")
            }
        }
    }
}
