package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.FileItem
import com.example.data.model.SortField
import com.example.data.model.SortOrder
import com.example.data.model.ViewMode
import com.example.ui.MainViewModel
import com.example.ui.components.*

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    viewModel: MainViewModel,
    onFileClick: (FileItem) -> Unit
) {
    val currentPath by viewModel.currentDirectoryPath.collectAsState()
    val files by viewModel.directoryFiles.collectAsState()
    val isLoading by viewModel.isLoadingDirectory.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val sortField by viewModel.sortField.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedPaths by viewModel.selectedPaths.collectAsState()

    var showSortDropdown by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    // Dialog state for single item action sheet / rename / info
    var activeActionFile by remember { mutableStateOf<FileItem?>(null) }
    var showRenameDialogForFile by remember { mutableStateOf<FileItem?>(null) }
    var showBatchRenameDialog by remember { mutableStateOf(false) }
    var showCompressDialogForSelection by remember { mutableStateOf(false) }
    var showDestinationPickerForMove by remember { mutableStateOf(false) }
    var showDestinationPickerForCopy by remember { mutableStateOf(false) }
    var showFileInfoSheetForFile by remember { mutableStateOf<FileItem?>(null) }
    var showFolderDeleteConfirm by remember { mutableStateOf<FileItem?>(null) }
    var filesToShareSheet by remember { mutableStateOf<List<FileItem>?>(null) }
    var showOpenWithSheetForFile by remember { mutableStateOf<FileItem?>(null) }
    var showTagSelectionDialog by remember { mutableStateOf(false) }

    val hasLocalDialog = activeActionFile != null ||
            showRenameDialogForFile != null ||
            showBatchRenameDialog ||
            showCompressDialogForSelection ||
            showDestinationPickerForMove ||
            showDestinationPickerForCopy ||
            showFileInfoSheetForFile != null ||
            showFolderDeleteConfirm != null ||
            filesToShareSheet != null ||
            showOpenWithSheetForFile != null ||
            showTagSelectionDialog

    BackHandler(enabled = hasLocalDialog) {
        activeActionFile = null
        showRenameDialogForFile = null
        showBatchRenameDialog = false
        showCompressDialogForSelection = false
        showDestinationPickerForMove = false
        showDestinationPickerForCopy = false
        showFileInfoSheetForFile = null
        showFolderDeleteConfirm = null
        filesToShareSheet = null
        showOpenWithSheetForFile = null
        showTagSelectionDialog = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isSelectionMode) {
                MultiSelectionTopBar(
                    selectedCount = selectedPaths.size,
                    totalItemsCount = files.size,
                    onCloseSelection = { viewModel.clearSelection() },
                    onSelectAll = { viewModel.selectAll() },
                    onShare = {
                        val selectedFiles = files.filter { selectedPaths.contains(it.path) }
                        if (selectedFiles.isNotEmpty()) {
                            filesToShareSheet = selectedFiles
                        }
                    },
                    onDelete = { viewModel.performDeleteSelected() },
                    onMove = { showDestinationPickerForMove = true },
                    onCopy = { showDestinationPickerForCopy = true },
                    onRename = {
                        val single = files.firstOrNull { selectedPaths.contains(it.path) }
                        if (single != null) {
                            showRenameDialogForFile = single
                        }
                    },
                    onBatchRename = { showBatchRenameDialog = true },
                    onTag = { showTagSelectionDialog = true },
                    onCompress = { showCompressDialogForSelection = true },
                    onToggleStar = {
                        val selectedFiles = files.filter { selectedPaths.contains(it.path) }
                        selectedFiles.forEach { viewModel.performToggleStar(it) }
                        viewModel.clearSelection()
                    },
                    onMoveToSafeFolder = {
                        val selectedFiles = files.filter { selectedPaths.contains(it.path) }
                        selectedFiles.forEach { viewModel.performMoveToSafeFolder(it) }
                        viewModel.clearSelection()
                    },
                    onInfo = {
                        val single = files.firstOrNull { selectedPaths.contains(it.path) }
                        if (single != null) {
                            showFileInfoSheetForFile = single
                        }
                    }
                )
            } else {
                // Breadcrumb Path Bar & View Options Row
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BreadcrumbNavigation(
                                currentPath = currentPath,
                                rootPath = viewModel.repository.rootPath,
                                onNavigateToPath = { path -> viewModel.loadDirectory(path) },
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            // View mode toggle with animated morphing icon
                            AnimatedIconButton(onClick = { viewModel.toggleViewMode() }) {
                                AnimatedToggleIcon(
                                    activeVector = Icons.Outlined.GridView,
                                    inactiveVector = Icons.Outlined.ViewList,
                                    isActive = viewMode == ViewMode.LIST,
                                    contentDescription = "Toggle view"
                                )
                            }

                            // Sort Order Toggle Button (Ascending / Descending)
                            IconButton(
                                onClick = { viewModel.toggleSortOrder() },
                                modifier = Modifier.testTag("toggle_sort_order_btn")
                            ) {
                                Icon(
                                    imageVector = if (sortOrder == SortOrder.ASCENDING) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                                    contentDescription = if (sortOrder == SortOrder.ASCENDING) "Ascending Order" else "Descending Order",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Sort dropdown trigger
                            Box {
                                IconButton(onClick = { showSortDropdown = true }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Sort,
                                        contentDescription = "Sort Options"
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSortDropdown,
                                    onDismissRequest = { showSortDropdown = false }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "SORT BY",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        },
                                        onClick = { },
                                        enabled = false
                                    )

                                    SortField.values().forEach { field ->
                                        DropdownMenuItem(
                                            text = { Text(field.name.lowercase().capitalize()) },
                                            trailingIcon = {
                                                if (sortField == field) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            },
                                            onClick = {
                                                showSortDropdown = false
                                                viewModel.setSortField(field)
                                            }
                                        )
                                    }

                                    Divider()

                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "ORDER",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        },
                                        onClick = { },
                                        enabled = false
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Ascending") },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
                                        },
                                        trailingIcon = {
                                            if (sortOrder == SortOrder.ASCENDING) {
                                                Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        },
                                        onClick = {
                                            showSortDropdown = false
                                            viewModel.setSortOrder(SortOrder.ASCENDING)
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Descending") },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                                        },
                                        trailingIcon = {
                                            if (sortOrder == SortOrder.DESCENDING) {
                                                Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        },
                                        onClick = {
                                            showSortDropdown = false
                                            viewModel.setSortOrder(SortOrder.DESCENDING)
                                        }
                                    )
                                }
                            }
                        }
                        Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }

            // Main Content: List or Grid or Loading Shimmer
            if (isLoading) {
                FileListShimmerPlaceholder(count = 7)
            } else if (files.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.FolderOpen,
                    title = "Folder is Empty",
                    description = "No files or subdirectories found in this directory.",
                    actionText = "Refresh Folder",
                    onAction = { viewModel.loadDirectory(currentPath) },
                    tipText = "Tip: Use the '+' FAB to create new folders, or pull down to reload file items.",
                    modifier = Modifier.weight(1f)
                )
            } else {
                if (viewMode == ViewMode.LIST) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp)
                            .dragSelectLazyList(
                                listState = listState,
                                items = files,
                                selectedPaths = selectedPaths,
                                onSelectionChange = { newPaths -> viewModel.setSelectedPaths(newPaths) }
                            ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(files, key = { it.path }) { file ->
                            val isSelected = selectedPaths.contains(file.path)
                            FileRowItem(
                                file = file,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleSelection(file.path)
                                    } else {
                                        if (file.isDirectory) {
                                            viewModel.loadDirectory(file.path)
                                        } else {
                                            onFileClick(file)
                                        }
                                    }
                                },
                                onLongClick = {
                                    viewModel.toggleSelection(file.path)
                                },
                                onOverflowClick = {
                                    activeActionFile = file
                                }
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .dragSelectLazyGrid(
                                gridState = gridState,
                                items = files,
                                selectedPaths = selectedPaths,
                                onSelectionChange = { newPaths -> viewModel.setSelectedPaths(newPaths) }
                            ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(files, key = { it.path }) { file ->
                            val isSelected = selectedPaths.contains(file.path)
                            FileGridItem(
                                file = file,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleSelection(file.path)
                                    } else {
                                        if (file.isDirectory) {
                                            viewModel.loadDirectory(file.path)
                                        } else {
                                            onFileClick(file)
                                        }
                                    }
                                },
                                onLongClick = {
                                    viewModel.toggleSelection(file.path)
                                },
                                onOverflowClick = {
                                    activeActionFile = file
                                }
                            )
                        }
                    }
                }
            }
        }

        // Single File Action Sheet
        if (activeActionFile != null) {
            val file = activeActionFile!!
            ModalBottomSheet(onDismissRequest = { activeActionFile = null }) {
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                    Divider()

                    if (file.isDirectory) {
                        // Folder Menu Items per Spec
                        DropdownMenuItem(
                            text = { Text("Select all") },
                            leadingIcon = { Icon(Icons.Outlined.SelectAll, contentDescription = null) },
                            onClick = {
                                activeActionFile = null
                                viewModel.selectAll()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Move to...") },
                            leadingIcon = { Icon(Icons.Outlined.DriveFileMove, contentDescription = null) },
                            onClick = {
                                activeActionFile = null
                                viewModel.toggleSelection(file.path)
                                showDestinationPickerForMove = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy to...") },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                            onClick = {
                                activeActionFile = null
                                viewModel.toggleSelection(file.path)
                                showDestinationPickerForCopy = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Compress") },
                            leadingIcon = { Icon(Icons.Outlined.FolderZip, contentDescription = null) },
                            onClick = {
                                activeActionFile = null
                                viewModel.toggleSelection(file.path)
                                showCompressDialogForSelection = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = {
                                activeActionFile = null
                                showRenameDialogForFile = file
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete permanently") },
                            leadingIcon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                activeActionFile = null
                                showFolderDeleteConfirm = file
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Folder Info") },
                            leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                            onClick = {
                                activeActionFile = null
                                showFileInfoSheetForFile = file
                            }
                        )
                    } else {
                        // Standard File Menu Items
                        DropdownMenuItem(
                            text = { Text("Open with...") },
                            leadingIcon = { Icon(Icons.Outlined.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                val target = file
                                activeActionFile = null
                                showOpenWithSheetForFile = target
                            }
                        )
                        if (file.name.endsWith(".zip", ignoreCase = true) || file.category == com.example.data.model.FileCategory.ARCHIVES) {
                            DropdownMenuItem(
                                text = { Text("Extract Here") },
                                leadingIcon = { Icon(Icons.Outlined.Unarchive, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    activeActionFile = null
                                    viewModel.performExtractZip(file.path)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Share via Quick Share") },
                            leadingIcon = { Icon(Icons.Outlined.WifiTethering, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                activeActionFile = null
                                filesToShareSheet = listOf(file)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Move to Bin") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = {
                                activeActionFile = null
                                viewModel.toggleSelection(file.path)
                                viewModel.performDeleteSelected()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (file.isStarred) "Remove from Starred" else "Star") },
                            leadingIcon = { Icon(Icons.Outlined.Star, contentDescription = null) },
                            onClick = {
                                activeActionFile = null
                                viewModel.performToggleStar(file)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Move to Safe folder") },
                            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                            onClick = {
                                activeActionFile = null
                                viewModel.performMoveToSafeFolder(file)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = {
                                activeActionFile = null
                                showRenameDialogForFile = file
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("File Info") },
                            leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                            onClick = {
                                activeActionFile = null
                                showFileInfoSheetForFile = file
                            }
                        )
                    }
                }
            }
        }

        // Folder Permanent Delete Confirmation Dialog
        if (showFolderDeleteConfirm != null) {
            val folder = showFolderDeleteConfirm!!
            AlertDialog(
                onDismissRequest = { showFolderDeleteConfirm = null },
                title = { Text("Delete Permanently?") },
                text = { Text("Folder '${folder.name}' and all its contents will be permanently deleted. This action cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            val path = folder.path
                            showFolderDeleteConfirm = null
                            viewModel.deleteFolderPermanently(path)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete Permanently")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFolderDeleteConfirm = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Rename Dialog
        if (showRenameDialogForFile != null) {
            val file = showRenameDialogForFile!!
            RenameDialog(
                initialName = file.name,
                onDismiss = { showRenameDialogForFile = null },
                onRename = { newName ->
                    showRenameDialogForFile = null
                    viewModel.performRenameSelected(file.path, newName)
                }
            )
        }

        // Batch Rename Dialog
        if (showBatchRenameDialog) {
            val selectedFiles = files.filter { selectedPaths.contains(it.path) }
            if (selectedFiles.isNotEmpty()) {
                BatchRenameDialog(
                    selectedFiles = selectedFiles,
                    onDismiss = { showBatchRenameDialog = false },
                    onApplyBatchRename = { renames ->
                        showBatchRenameDialog = false
                        viewModel.performBatchRename(renames)
                    }
                )
            }
        }

        // Compress Dialog
        if (showCompressDialogForSelection) {
            CompressDialog(
                defaultZipName = "Archive_${System.currentTimeMillis().toString().takeLast(4)}",
                onDismiss = { showCompressDialogForSelection = false },
                onCompress = { zipName ->
                    showCompressDialogForSelection = false
                    viewModel.performCompressSelected(zipName)
                }
            )
        }

        // Destination Folder Pickers
        if (showDestinationPickerForMove) {
            DestinationFolderPickerBottomSheet(
                currentRoot = viewModel.repository.rootPath,
                onDismiss = { showDestinationPickerForMove = false },
                onSelectDestination = { destPath ->
                    showDestinationPickerForMove = false
                    viewModel.performMoveSelected(destPath)
                }
            )
        }

        if (showDestinationPickerForCopy) {
            DestinationFolderPickerBottomSheet(
                currentRoot = viewModel.repository.rootPath,
                onDismiss = { showDestinationPickerForCopy = false },
                onSelectDestination = { destPath ->
                    showDestinationPickerForCopy = false
                    viewModel.performCopySelected(destPath)
                }
            )
        }

        // File Details Sheet
        if (showFileInfoSheetForFile != null) {
            FileInfoBottomSheet(
                file = showFileInfoSheetForFile!!,
                onDismiss = { showFileInfoSheetForFile = null }
            )
        }

        // Quick Share File Action Sheet
        if (filesToShareSheet != null) {
            FileShareActionSheet(
                filesToShare = filesToShareSheet!!,
                onDismiss = { filesToShareSheet = null },
                onShareSuccess = { msg ->
                    viewModel.emitSnackbar(msg)
                }
            )
        }

        // Open with Intent Selector Sheet
        if (showOpenWithSheetForFile != null) {
            OpenWithBottomSheet(
                file = showOpenWithSheetForFile!!,
                onDismiss = { showOpenWithSheetForFile = null },
                onOpenWithBuiltInViewer = {
                    val target = showOpenWithSheetForFile!!
                    showOpenWithSheetForFile = null
                    onFileClick(target)
                },
                onOpenSuccess = { msg ->
                    viewModel.emitSnackbar(msg)
                }
            )
        }

        if (showTagSelectionDialog) {
            TagSelectionDialog(
                selectedCount = selectedPaths.size,
                viewModel = viewModel,
                onDismiss = { showTagSelectionDialog = false },
                onTagSelected = { tagId, tagName ->
                    showTagSelectionDialog = false
                    viewModel.addTagToFiles(selectedPaths, tagId, tagName)
                }
            )
        }
    }
}
