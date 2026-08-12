package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.FileCategory
import com.example.data.model.FileItem
import com.example.ui.MainViewModel
import com.example.ui.components.AnimatedIconButton
import com.example.ui.components.EmptyState
import com.example.ui.components.FileListShimmerPlaceholder
import com.example.ui.components.FileRowItem
import com.example.ui.components.getCategoryIconAndColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    category: FileCategory,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onFileClick: (FileItem) -> Unit
) {
    val categoryFiles by viewModel.categoryFiles.collectAsState()
    val isLoading by viewModel.isLoadingDirectory.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedPaths by viewModel.selectedPaths.collectAsState()
    val (icon, tintColor) = getCategoryIconAndColor(category)
    var shareFile by remember { mutableStateOf<FileItem?>(null) }
    var showTagSelectionDialog by remember { mutableStateOf(false) }

    val allCategories = remember {
        listOf(
            FileCategory.IMAGES,
            FileCategory.DOCUMENTS,
            FileCategory.AUDIO,
            FileCategory.VIDEOS,
            FileCategory.DOWNLOADS,
            FileCategory.APPS,
            FileCategory.ARCHIVES,
            FileCategory.SCREENSHOTS
        )
    }

    val selectedTabIndex = remember(category) {
        allCategories.indexOf(category).coerceAtLeast(0)
    }

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
                com.example.ui.components.MultiSelectionTopBar(
                    selectedCount = selectedPaths.size,
                    totalItemsCount = categoryFiles.size,
                    onCloseSelection = { viewModel.clearSelection() },
                    onSelectAll = { viewModel.selectAll() },
                    onShare = {
                        val selectedFiles = categoryFiles.filter { selectedPaths.contains(it.path) }
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
                        val selectedFiles = categoryFiles.filter { selectedPaths.contains(it.path) }
                        selectedFiles.forEach { viewModel.performToggleStar(it) }
                        viewModel.clearSelection()
                    },
                    onMoveToSafeFolder = {
                        val selectedFiles = categoryFiles.filter { selectedPaths.contains(it.path) }
                        selectedFiles.forEach { viewModel.performMoveToSafeFolder(it) }
                        viewModel.clearSelection()
                    },
                    onInfo = { }
                )
            } else {
                TopAppBar(
                    title = { Text(category.displayName) },
                    navigationIcon = {
                        AnimatedIconButton(onClick = onBack, modifier = Modifier.testTag("category_back_btn")) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                viewModel.openCategory(category)
                                viewModel.emitSnackbar("Auto-scanned file extensions & metadata for ${category.displayName}")
                            },
                            modifier = Modifier.testTag("rescan_category_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = "Scan & Classify",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Automated File Classification Category Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("category_tab_row")
            ) {
                allCategories.forEachIndexed { index, cat ->
                    val (catIcon, catColor) = getCategoryIconAndColor(cat)
                    val isSelected = index == selectedTabIndex
                    Tab(
                        selected = isSelected,
                        onClick = {
                            if (cat != category) {
                                viewModel.openCategory(cat)
                            }
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = catIcon,
                                    contentDescription = null,
                                    tint = if (isSelected) catColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = cat.displayName,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier.testTag("category_tab_${cat.name.lowercase()}")
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (isLoading) {
                    FileListShimmerPlaceholder(count = 6)
                } else if (categoryFiles.isEmpty()) {
                    EmptyState(
                        icon = icon,
                        title = "No ${category.displayName} Found",
                        description = "Files matching this category will appear here automatically via extension and metadata scanning.",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(categoryFiles, key = { it.path }) { file ->
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
                    com.example.ui.components.TagSelectionDialog(
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
    }
}
