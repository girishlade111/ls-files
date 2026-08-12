package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.FileItem
import com.example.ui.MainViewModel
import com.example.ui.components.AnimatedIconButton
import com.example.ui.components.AnimatedToggleIcon
import com.example.ui.components.EmptyState
import com.example.ui.components.FileRowItem
import com.example.ui.components.SafeFolderLockDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafeFolderScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onFileClick: (FileItem) -> Unit
) {
    val isUnlocked by viewModel.isSafeFolderUnlocked.collectAsState()
    val savedPin by viewModel.safeFolderPin.collectAsState()
    val safeFiles by viewModel.safeFolderFiles.collectAsState()

    var showLockDialog by remember { mutableStateOf(!isUnlocked) }
    var actionTargetFile by remember { mutableStateOf<FileItem?>(null) }

    BackHandler(enabled = true) {
        if (actionTargetFile != null) {
            actionTargetFile = null
        } else if (showLockDialog && isUnlocked) {
            showLockDialog = false
        } else {
            if (isUnlocked) {
                viewModel.lockSafeFolder()
            }
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Safe Folder") },
                navigationIcon = {
                    AnimatedIconButton(onClick = onBack, modifier = Modifier.testTag("safe_folder_back_btn")) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    AnimatedIconButton(
                        onClick = {
                            if (isUnlocked) {
                                viewModel.lockSafeFolder()
                                showLockDialog = true
                            } else {
                                showLockDialog = true
                            }
                        },
                        modifier = Modifier.testTag("lock_safe_folder_btn")
                    ) {
                        AnimatedToggleIcon(
                            activeVector = Icons.Outlined.LockOpen,
                            inactiveVector = Icons.Outlined.Lock,
                            isActive = isUnlocked,
                            contentDescription = if (isUnlocked) "Lock Safe Folder" else "Unlock Safe Folder"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!isUnlocked) {
                EmptyState(
                    icon = Icons.Outlined.Lock,
                    title = "Safe Folder Locked",
                    description = "Protected by AES-256 local encrypted vault and Biometric / PIN authentication.",
                    actionText = if (savedPin == null) "Set Up PIN" else "Unlock Safe Folder",
                    onAction = { showLockDialog = true },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (safeFiles.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.LockOpen,
                    title = "Safe Folder is Empty",
                    description = "Move sensitive files here from Browse or Home to store them securely and hide them from standard file views.",
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
                    items(safeFiles, key = { it.path }) { file ->
                        Box {
                            FileRowItem(
                                file = file,
                                isSelected = false,
                                isSelectionMode = false,
                                onClick = { onFileClick(file) },
                                onLongClick = { actionTargetFile = file },
                                onOverflowClick = { actionTargetFile = file }
                            )

                            DropdownMenu(
                                expanded = actionTargetFile?.path == file.path,
                                onDismissRequest = { actionTargetFile = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("View File") },
                                    leadingIcon = { Icon(Icons.Outlined.Visibility, contentDescription = null) },
                                    onClick = {
                                        actionTargetFile = null
                                        onFileClick(file)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Restore to Documents") },
                                    leadingIcon = { Icon(Icons.Outlined.LockReset, contentDescription = null) },
                                    onClick = {
                                        actionTargetFile = null
                                        viewModel.performRemoveFromSafeFolder(file)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Permanently") },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        actionTargetFile = null
                                        viewModel.performDeleteFromSafeFolder(file)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLockDialog && !isUnlocked) {
        SafeFolderLockDialog(
            isFirstSetup = savedPin == null,
            onDismiss = {
                showLockDialog = false
                if (!isUnlocked) onBack()
            },
            onSuccess = { pin ->
                if (savedPin == null) {
                    viewModel.setSafeFolderPin(pin)
                    showLockDialog = false
                } else {
                    val success = viewModel.unlockSafeFolder(pin)
                    if (success) {
                        showLockDialog = false
                    } else {
                        viewModel.emitSnackbar("Incorrect PIN")
                    }
                }
            },
            onBiometricSuccess = {
                viewModel.unlockSafeFolderWithBiometric()
                showLockDialog = false
            }
        )
    }
}

