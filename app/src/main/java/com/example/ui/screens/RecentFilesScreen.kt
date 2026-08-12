package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.FileItem
import com.example.ui.MainViewModel
import com.example.ui.components.AnimatedIconButton
import com.example.ui.components.EmptyState
import com.example.ui.components.FileRowItem
import com.example.ui.components.MultiSelectionTopBar
import com.example.ui.components.TagSelectionDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentFilesScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit = {},
    onFileClick: (FileItem) -> Unit = {}
) {
    val recents by viewModel.recentFiles.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedPaths by viewModel.selectedPaths.collectAsState()
    var shareFile by remember { mutableStateOf<FileItem?>(null) }
    var showTagSelectionDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (shareFile != null) {
            shareFile = null
        } else if (showTagSelectionDialog) {
            showTagSelectionDialog = false
        } else if (isSelectionMode) {
            viewModel.clearSelection()
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                MultiSelectionTopBar(
                    selectedCount = selectedPaths.size,
                    totalItemsCount = recents.size,
                    onCloseSelection = { viewModel.clearSelection() },
                    onSelectAll = { viewModel.selectAll() },
                    onShare = {
                        val selectedFiles = recents.filter { selectedPaths.contains(it.path) }
                        if (selectedFiles.isNotEmpty()) {
                            shareFile = selectedFiles.firstOrNull()
                        }
                    },
                    onDelete = { viewModel.performDeleteSelected() },
                    onMove = { viewModel.emitSnackbar("Select a destination folder in Browse tab") },
                    onCopy = { viewModel.emitSnackbar("Select a destination folder in Browse tab") },
                    onRename = { },
                    onTag = { showTagSelectionDialog = true },
                    onCompress = { },
                    onToggleStar = {
                        val selectedFiles = recents.filter { selectedPaths.contains(it.path) }
                        selectedFiles.forEach { viewModel.performToggleStar(it) }
                        viewModel.clearSelection()
                    },
                    onMoveToSafeFolder = {
                        val selectedFiles = recents.filter { selectedPaths.contains(it.path) }
                        selectedFiles.forEach { viewModel.performMoveToSafeFolder(it) }
                        viewModel.clearSelection()
                    },
                    onInfo = { }
                )
            } else {
                TopAppBar(
                    title = { Text("Recent Files") },
                    navigationIcon = {
                        AnimatedIconButton(onClick = onBack, modifier = Modifier.testTag("recent_back_btn")) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        if (recents.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.History,
                title = "No Recent File Activity",
                description = "Files opened, viewed, or extracted will automatically appear here for quick access.",
                actionText = "Explore Files",
                onAction = onBack,
                tipText = "Tip: Accessing files from Browse or Search pins them here automatically.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(recents, key = { it.path }) { file ->
                    val isSelected = selectedPaths.contains(file.path)
                    FileRowItem(
                        file = file,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        onClick = {
                            if (isSelectionMode) {
                                viewModel.toggleSelection(file.path)
                            } else {
                                onFileClick(file)
                            }
                        },
                        onLongClick = {
                            viewModel.toggleSelection(file.path)
                        },
                        onOverflowClick = { shareFile = file }
                    )
                }
            }
        }

        if (shareFile != null) {
            com.example.ui.components.FileShareActionSheet(
                filesToShare = listOf(shareFile!!),
                onDismiss = { shareFile = null },
                onShareSuccess = { msg -> viewModel.emitSnackbar(msg) }
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
