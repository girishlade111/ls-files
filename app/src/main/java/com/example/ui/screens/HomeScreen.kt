package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.example.data.db.*
import com.example.data.model.CloudProviderType
import com.example.data.model.FileCategory
import com.example.data.model.FileItem
import com.example.ui.NavDestination
import com.example.ui.components.AnimatedCategoryIcon
import com.example.ui.components.AnimatedIconButton
import com.example.ui.components.AnimatedPulseIcon
import com.example.ui.components.CategoriesGrid
import com.example.ui.components.CloudAccountsList
import com.example.ui.components.StorageBreakdownPieChart
import com.example.ui.components.formatFileSize
import com.example.ui.components.formatTimestamp
import com.example.ui.components.getFileIconAndColor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.CheckCircle
import com.example.ui.components.MultiSelectionTopBar
import com.example.ui.components.RenameDialog
import com.example.ui.components.BatchRenameDialog
import com.example.ui.components.dragSelectLazyRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: com.example.ui.MainViewModel,
    onOpenDrawer: () -> Unit,
    onOpenCategory: (FileCategory) -> Unit,
    onOpenRecent: () -> Unit,
    onOpenStarred: () -> Unit,
    onOpenSafeFolder: () -> Unit,
    onOpenCloudConnect: () -> Unit,
    onFileClick: (FileItem) -> Unit
) {
    val storageInfo by viewModel.storageSpaceInfo.collectAsState()
    val categorySizes by viewModel.categorySizes.collectAsState()
    val recentFiles by viewModel.recentFiles.collectAsState()
    val recentIndexedFiles by viewModel.recentIndexedFiles.collectAsState()
    val cloudAccounts by viewModel.cloudAccountsFlow.collectAsState(initial = emptyList())
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedPaths by viewModel.selectedPaths.collectAsState()
    var filesToShareSheet by remember { mutableStateOf<List<FileItem>?>(null) }
    var showRenameDialogForFile by remember { mutableStateOf<FileItem?>(null) }
    var showBatchRenameDialog by remember { mutableStateOf(false) }
    var showTagSelectionDialog by remember { mutableStateOf(false) }

    val displayRecentIndexedFiles = remember(recentIndexedFiles, recentFiles) {
        if (recentIndexedFiles.isNotEmpty()) {
            recentIndexedFiles.take(5)
        } else {
            recentFiles.take(5)
        }
    }

    val hasHomeDialog = filesToShareSheet != null ||
            showRenameDialogForFile != null ||
            showBatchRenameDialog ||
            showTagSelectionDialog

    BackHandler(enabled = hasHomeDialog) {
        filesToShareSheet = null
        showRenameDialogForFile = null
        showBatchRenameDialog = false
        showTagSelectionDialog = false
    }

    val recentRowState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 90.dp) // Room for dual FAB
        ) {
            if (isSelectionMode) {
                MultiSelectionTopBar(
                    selectedCount = selectedPaths.size,
                    totalItemsCount = recentFiles.size,
                    onCloseSelection = { viewModel.clearSelection() },
                    onSelectAll = { viewModel.selectAll() },
                    onShare = {
                        val selectedFiles = recentFiles.filter { selectedPaths.contains(it.path) }
                        if (selectedFiles.isNotEmpty()) {
                            filesToShareSheet = selectedFiles
                        }
                    },
                    onDelete = { viewModel.performDeleteSelected() },
                    onMove = { viewModel.emitSnackbar("Select a destination folder in Browse tab") },
                    onCopy = { viewModel.emitSnackbar("Select a destination folder in Browse tab") },
                    onRename = {
                        val single = recentFiles.firstOrNull { selectedPaths.contains(it.path) }
                        if (single != null) {
                            showRenameDialogForFile = single
                        }
                    },
                    onBatchRename = { showBatchRenameDialog = true },
                    onTag = { showTagSelectionDialog = true },
                    onCompress = { },
                    onToggleStar = {
                        val selectedFiles = recentFiles.filter { selectedPaths.contains(it.path) }
                        selectedFiles.forEach { viewModel.performToggleStar(it) }
                        viewModel.clearSelection()
                    },
                    onMoveToSafeFolder = {
                        val selectedFiles = recentFiles.filter { selectedPaths.contains(it.path) }
                        selectedFiles.forEach { viewModel.performMoveToSafeFolder(it) }
                        viewModel.clearSelection()
                    },
                    onInfo = { }
                )
            } else {
                // Pinned Top App Bar Search Banner
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.navigateTo(NavDestination.SEARCH) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Search files and documents"
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedIconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("home_hamburger_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Menu,
                                contentDescription = "Menu",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Search files & documents...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Recent Files Horizontal List (Latest 5 entries from Indexed Database)
            if (displayRecentIndexedFiles.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Recent Files",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "Top 5 Indexed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    TextButton(
                        onClick = onOpenRecent,
                        modifier = Modifier.testTag("see_all_recent")
                    ) {
                        Text("See all")
                    }
                }

                LazyRow(
                    state = recentRowState,
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.dragSelectLazyRow(
                        listState = recentRowState,
                        items = displayRecentIndexedFiles,
                        selectedPaths = selectedPaths,
                        onSelectionChange = { newPaths -> viewModel.setSelectedPaths(newPaths) }
                    )
                ) {
                    items(displayRecentIndexedFiles) { file ->
                        val isSelected = selectedPaths.contains(file.path)
                        RecentFileCard(
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
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // Categories Grid Component
            CategoriesGrid(
                categorySizes = categorySizes,
                onCategoryClick = onOpenCategory,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Collections Section
            Text(
                text = "Collections",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Starred Card
                Card(
                    onClick = onOpenStarred,
                    modifier = Modifier
                        .weight(1f)
                        .height(80.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFB300).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedCategoryIcon(
                                imageVector = Icons.Outlined.Star,
                                contentDescription = "Starred",
                                tint = Color(0xFFFFB300),
                                iconSize = 22.dp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Starred",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Favorites",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Safe Folder Card
                Card(
                    onClick = onOpenSafeFolder,
                    modifier = Modifier
                        .weight(1f)
                        .height(80.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedPulseIcon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = "Safe Folder",
                                tint = MaterialTheme.colorScheme.tertiary,
                                isPulsing = true
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Safe folder",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Encrypted",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Power Tools Section
            Text(
                text = "Power Tools & Utilities",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Cleaner Card
                Card(
                    onClick = { viewModel.navigateTo(com.example.ui.NavDestination.CLEANER) },
                    modifier = Modifier.weight(1f).height(80.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Outlined.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Cleaner", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }

                // Wi-Fi Transfer Card
                Card(
                    onClick = { viewModel.navigateTo(com.example.ui.NavDestination.WIFI_TRANSFER) },
                    modifier = Modifier.weight(1f).height(80.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Outlined.WifiTethering, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Wi-Fi PC", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }

                // Media Tools Card
                Card(
                    onClick = { viewModel.navigateTo(com.example.ui.NavDestination.TOOLS) },
                    modifier = Modifier.weight(1f).height(80.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Outlined.Build, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("PDF/Media", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // All Storage Section
            Text(
                text = "All storage",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            // Visual Storage Breakdown Pie Chart Component
            StorageBreakdownPieChart(
                categorySizes = categorySizes,
                storageInfo = storageInfo,
                onCategoryClick = onOpenCategory,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Internal Storage Row Card
            Card(
                onClick = { viewModel.navigateTo(NavDestination.BROWSE) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhoneAndroid,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Internal storage",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = String.format("%.1f GB free of %.1f GB", storageInfo.freeGb, storageInfo.totalGb),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { storageInfo.usedRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            // Cloud Accounts List Component
            Spacer(modifier = Modifier.height(16.dp))
            CloudAccountsList(
                accounts = cloudAccounts,
                onAccountClick = { viewModel.navigateTo(NavDestination.BROWSE) },
                onDisconnectAccount = { account -> viewModel.disconnectCloudAccount(account.accountId) },
                onAddAccount = onOpenCloudConnect,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        // Dual FAB Cluster Bottom Right
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Secondary FAB: Rescan storage index
            SmallFloatingActionButton(
                onClick = {
                    viewModel.refreshStorageInfo()
                    viewModel.refreshCategories()
                    viewModel.refreshRecentFiles()
                    viewModel.emitSnackbar("Storage index re-scanned")
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.testTag("rescan_index_fab")
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Rescan index")
            }

            // Primary FAB: Scan document
            ExtendedFloatingActionButton(
                onClick = { viewModel.performScanDocument() },
                icon = { Icon(Icons.Outlined.DocumentScanner, contentDescription = null) },
                text = { Text("Scan document") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("scan_doc_fab")
            )
        }

        if (filesToShareSheet != null) {
            com.example.ui.components.FileShareActionSheet(
                filesToShare = filesToShareSheet!!,
                onDismiss = { filesToShareSheet = null },
                onShareSuccess = { msg -> viewModel.emitSnackbar(msg) }
            )
        }

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

        if (showBatchRenameDialog) {
            val selectedFiles = recentFiles.filter { selectedPaths.contains(it.path) }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentFileCard(
    file: FileItem,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val (icon, tintColor) = getFileIconAndColor(file)

    Card(
        modifier = Modifier
            .width(140.dp)
            .height(110.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(tintColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tintColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatTimestamp(file.lastModified),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isSelectionMode && isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(22.dp)
                )
            }
        }
    }
}
