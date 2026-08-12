package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BinItem
import com.example.data.model.FileItem
import com.example.ui.MainViewModel
import com.example.ui.components.AnimatedIconButton
import com.example.ui.components.DestinationFolderPickerBottomSheet
import com.example.ui.components.EmptyState
import com.example.ui.components.formatFileSize
import com.example.ui.util.HapticType
import com.example.ui.util.rememberAppHapticFeedback
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit = {},
    onFileClick: (FileItem) -> Unit = {}
) {
    val haptic = rememberAppHapticFeedback()
    val binEntities by viewModel.binItemsFlow.collectAsState(initial = emptyList())

    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showEmptyBinConfirm by remember { mutableStateOf(false) }
    var itemsToRestorePending by remember { mutableStateOf<List<BinItem>?>(null) }
    var itemsToRestoreCustom by remember { mutableStateOf<List<BinItem>?>(null) }
    var showDestinationPicker by remember { mutableStateOf(false) }
    var showDeleteConfirmForSelected by remember { mutableStateOf(false) }

    val isBinLocalActive = selectedPaths.isNotEmpty() ||
            showEmptyBinConfirm ||
            itemsToRestorePending != null ||
            itemsToRestoreCustom != null ||
            showDestinationPicker ||
            showDeleteConfirmForSelected

    BackHandler(enabled = isBinLocalActive) {
        selectedPaths = emptySet()
        showEmptyBinConfirm = false
        itemsToRestorePending = null
        itemsToRestoreCustom = null
        showDestinationPicker = false
        showDeleteConfirmForSelected = false
    }

    val binItems = remember(binEntities) {
        binEntities.map { entity ->
            BinItem(
                filePath = entity.filePath,
                fileName = entity.fileName,
                originalPath = entity.originalPath,
                fileSize = entity.fileSize,
                mimeType = entity.mimeType,
                deletedTimestamp = entity.deletedTimestamp
            )
        }
    }

    val isSelectionMode = selectedPaths.isNotEmpty()

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedPaths.size} selected") },
                    navigationIcon = {
                        AnimatedIconButton(
                            onClick = { selectedPaths = emptySet() },
                            modifier = Modifier.testTag("close_bin_selection")
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        AnimatedIconButton(
                            onClick = {
                                if (selectedPaths.size == binItems.size) {
                                    selectedPaths = emptySet()
                                } else {
                                    selectedPaths = binItems.map { it.filePath }.toSet()
                                }
                            }
                        ) {
                            Icon(
                                if (selectedPaths.size == binItems.size) Icons.Outlined.Deselect else Icons.Outlined.SelectAll,
                                contentDescription = "Toggle select all"
                            )
                        }
                        AnimatedIconButton(
                            onClick = {
                                itemsToRestorePending = binItems.filter { selectedPaths.contains(it.filePath) }
                            },
                            modifier = Modifier.testTag("restore_selected_btn")
                        ) {
                            Icon(
                                Icons.Outlined.RestoreFromTrash,
                                contentDescription = "Restore selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        AnimatedIconButton(
                            onClick = { showDeleteConfirmForSelected = true },
                            modifier = Modifier.testTag("delete_selected_permanently_btn")
                        ) {
                            Icon(
                                Icons.Outlined.DeleteForever,
                                contentDescription = "Delete selected permanently",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Bin") },
                    navigationIcon = {
                        AnimatedIconButton(onClick = onBack, modifier = Modifier.testTag("bin_back_btn")) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (binItems.isNotEmpty()) {
                            TextButton(
                                onClick = { showEmptyBinConfirm = true },
                                modifier = Modifier.testTag("empty_bin_btn")
                            ) {
                                Text("Empty Bin", color = MaterialTheme.colorScheme.error)
                            }
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
            // Retention Notice
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Items in the Bin are permanently deleted after 30 days.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (binItems.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.DeleteSweep,
                    title = "Bin is empty",
                    description = "Deleted items will remain in the Bin for 30 days before permanent removal.",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(binItems, key = { it.filePath }) { item ->
                        val isSelected = selectedPaths.contains(item.filePath)
                        BinItemRow(
                            item = item,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) {
                                    haptic.perform(HapticType.SELECTION_TOGGLE)
                                    selectedPaths = if (isSelected) {
                                        selectedPaths - item.filePath
                                    } else {
                                        selectedPaths + item.filePath
                                    }
                                }
                            },
                            onLongClick = {
                                haptic.perform(HapticType.LONG_PRESS)
                                selectedPaths = if (isSelected) {
                                    selectedPaths - item.filePath
                                } else {
                                    selectedPaths + item.filePath
                                }
                            },
                            onRestore = {
                                itemsToRestorePending = listOf(item)
                            },
                            onDeletePermanently = {
                                viewModel.deleteBinPermanently(item.filePath)
                            }
                        )
                    }
                }
            }
        }
    }

    // Restore dialog options
    if (itemsToRestorePending != null) {
        val targets = itemsToRestorePending!!
        RestoreLocationDialog(
            itemsToRestore = targets,
            onDismiss = { itemsToRestorePending = null },
            onRestoreToOriginal = {
                viewModel.restoreBinItems(targets.map { it.filePath }, customDestinationDir = null)
                selectedPaths = selectedPaths - targets.map { it.filePath }.toSet()
                itemsToRestorePending = null
            },
            onChooseCustomLocation = {
                itemsToRestoreCustom = targets
                itemsToRestorePending = null
                showDestinationPicker = true
            }
        )
    }

    // Custom folder picker for restore
    if (showDestinationPicker && itemsToRestoreCustom != null) {
        DestinationFolderPickerBottomSheet(
            currentRoot = viewModel.repository.rootPath,
            onDismiss = {
                showDestinationPicker = false
                itemsToRestoreCustom = null
            },
            onSelectDestination = { customDir ->
                val targets = itemsToRestoreCustom!!
                viewModel.restoreBinItems(targets.map { it.filePath }, customDestinationDir = customDir)
                selectedPaths = selectedPaths - targets.map { it.filePath }.toSet()
                showDestinationPicker = false
                itemsToRestoreCustom = null
            }
        )
    }

    // Empty bin confirmation
    if (showEmptyBinConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyBinConfirm = false },
            title = { Text("Empty Bin?") },
            text = { Text("All ${binItems.size} items in the Bin will be permanently removed. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showEmptyBinConfirm = false
                        viewModel.emptyBin()
                        selectedPaths = emptySet()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Empty Bin")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyBinConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete selected items permanently confirmation
    if (showDeleteConfirmForSelected) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmForSelected = false },
            title = { Text("Delete Permanently?") },
            text = { Text("Are you sure you want to permanently remove ${selectedPaths.size} selected item(s)? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmForSelected = false
                        val targets = selectedPaths.toList()
                        selectedPaths = emptySet()
                        targets.forEach { path ->
                            viewModel.deleteBinPermanently(path)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmForSelected = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun RestoreLocationDialog(
    itemsToRestore: List<BinItem>,
    onDismiss: () -> Unit,
    onRestoreToOriginal: () -> Unit,
    onChooseCustomLocation: () -> Unit
) {
    var selectedOption by remember { mutableStateOf(0) } // 0 = Original, 1 = Custom

    val displayOriginalPath = remember(itemsToRestore) {
        if (itemsToRestore.size == 1) {
            File(itemsToRestore.first().originalPath).parent ?: "Original location"
        } else {
            "Original file paths"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (itemsToRestore.size == 1) "Restore '${itemsToRestore.first().fileName}'" else "Restore ${itemsToRestore.size} Items",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Select where to restore the file(s):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    onClick = { selectedOption = 0 },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedOption == 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = if (selectedOption == 0) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedOption == 0,
                            onClick = { selectedOption = 0 }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Original Location",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = displayOriginalPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Surface(
                    onClick = { selectedOption = 1 },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedOption == 1) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = if (selectedOption == 1) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedOption == 1,
                            onClick = { selectedOption = 1 }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Choose Custom Location...",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Pick a specific folder to restore into",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedOption == 0) {
                        onRestoreToOriginal()
                    } else {
                        onChooseCustomLocation()
                    }
                },
                modifier = Modifier.testTag("confirm_restore_btn")
            ) {
                Text("Restore")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BinItemRow(
    item: BinItem,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit
) {
    val parentDirName = remember(item.originalPath) {
        val parent = File(item.originalPath).parent
        if (parent != null) File(parent).name else "Root"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            Icon(
                Icons.Outlined.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            text = "Deletes in ${item.daysRemaining} days",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${formatFileSize(item.fileSize)} • From $parentDirName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!isSelectionMode) {
                AnimatedIconButton(onClick = onRestore, modifier = Modifier.testTag("row_restore_btn")) {
                    Icon(
                        Icons.Outlined.RestoreFromTrash,
                        contentDescription = "Restore",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                AnimatedIconButton(onClick = onDeletePermanently, modifier = Modifier.testTag("row_delete_btn")) {
                    Icon(
                        Icons.Outlined.DeleteForever,
                        contentDescription = "Delete permanently",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
