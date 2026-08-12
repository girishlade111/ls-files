package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectionTopBar(
    selectedCount: Int,
    totalItemsCount: Int,
    onCloseSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onRename: () -> Unit,
    onBatchRename: () -> Unit = {},
    onTag: () -> Unit = {},
    onCompress: () -> Unit,
    onToggleStar: () -> Unit,
    onMoveToSafeFolder: () -> Unit,
    onInfo: () -> Unit
) {
    var showOverflowMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        },
        navigationIcon = {
            AnimatedIconButton(
                onClick = onCloseSelection,
                modifier = Modifier.testTag("close_selection")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Exit Selection"
                )
            }
        },
        actions = {
            AnimatedIconButton(onClick = onShare) {
                Icon(imageVector = Icons.Outlined.Share, contentDescription = "Share")
            }
            AnimatedIconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Outlined.Delete, contentDescription = "Delete")
            }
            AnimatedIconButton(onClick = onMove) {
                Icon(imageVector = Icons.Outlined.DriveFileMove, contentDescription = "Move")
            }
            AnimatedIconButton(onClick = onTag, modifier = Modifier.testTag("selection_tag_action")) {
                Icon(imageVector = Icons.Outlined.Label, contentDescription = "Tag")
            }
            Box {
                AnimatedIconButton(
                    onClick = { showOverflowMenu = true },
                    modifier = Modifier.testTag("selection_more_overflow")
                ) {
                    Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Select all ($totalItemsCount)") },
                        leadingIcon = { Icon(Icons.Outlined.SelectAll, contentDescription = null) },
                        onClick = {
                            showOverflowMenu = false
                            onSelectAll()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy to...") },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                        onClick = {
                            showOverflowMenu = false
                            onCopy()
                        }
                    )
                    if (selectedCount == 1) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onRename()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Batch Rename") },
                        leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null) },
                        onClick = {
                            showOverflowMenu = false
                            onBatchRename()
                        },
                        modifier = Modifier.testTag("batch_rename_menu_item")
                    )
                    DropdownMenuItem(
                        text = { Text("Tag selected files") },
                        leadingIcon = { Icon(Icons.Outlined.Label, contentDescription = null) },
                        onClick = {
                            showOverflowMenu = false
                            onTag()
                        },
                        modifier = Modifier.testTag("tag_files_menu_item")
                    )
                    DropdownMenuItem(
                        text = { Text("Compress") },
                        leadingIcon = { Icon(Icons.Outlined.FolderZip, contentDescription = null) },
                        onClick = {
                            showOverflowMenu = false
                            onCompress()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Star / Unstar") },
                        leadingIcon = { Icon(Icons.Outlined.Star, contentDescription = null) },
                        onClick = {
                            showOverflowMenu = false
                            onToggleStar()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Move to Safe folder") },
                        leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                        onClick = {
                            showOverflowMenu = false
                            onMoveToSafeFolder()
                        }
                    )
                    if (selectedCount == 1) {
                        DropdownMenuItem(
                            text = { Text("File Info") },
                            leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onInfo()
                            }
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}
