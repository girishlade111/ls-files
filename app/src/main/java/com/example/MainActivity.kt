package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.data.model.FileItem
import com.example.ui.MainViewModel
import com.example.ui.NavDestination
import com.example.ui.UIEvent
import com.example.permission.StoragePermissionManager
import com.example.ui.components.AnimatedNavIcon
import com.example.ui.components.CloudConnectDialog
import com.example.ui.components.FileViewerDialog
import com.example.ui.components.NavigationDrawerContent
import com.example.ui.components.PermissionRationaleDialog
import com.example.ui.components.StoragePermissionPromptCard
import com.example.ui.components.ZipProgressDialog
import com.example.ui.components.OpenWithBottomSheet
import com.example.ui.screens.*
import com.example.ui.theme.LsFilesTheme
import com.example.ui.util.rememberAppHapticFeedback
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var storagePermissionManager: StoragePermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        storagePermissionManager = StoragePermissionManager(this)

        setContent {
            LsFilesTheme {
                MainAppScreen(
                    viewModel = viewModel,
                    storagePermissionManager = storagePermissionManager,
                    onRequestAllFilesPermission = { storagePermissionManager.requestStoragePermission() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh storage and files on resume
        viewModel.refreshStorageInfo()
        viewModel.refreshCategories()
        viewModel.refreshRecentFiles()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: MainViewModel,
    storagePermissionManager: StoragePermissionManager,
    onRequestAllFilesPermission: () -> Unit
) {
    val appHaptic = rememberAppHapticFeedback()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentDestination by viewModel.currentDestination.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val zipProgress by viewModel.zipProgress.collectAsState()

    var showCloudConnectDialog by remember { mutableStateOf(false) }
    var viewingFile by remember { mutableStateOf<FileItem?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showOpenWithFile by remember { mutableStateOf<FileItem?>(null) }

    // Check external storage permission using StoragePermissionManager
    LaunchedEffect(Unit) {
        if (!storagePermissionManager.hasStoragePermission()) {
            showPermissionDialog = true
        }
    }

    // Listen for UI events (snackbars and haptics)
    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UIEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = event.actionLabel,
                        duration = SnackbarDuration.Short
                    )
                }
                is UIEvent.TriggerHaptic -> {
                    appHaptic.perform(event.type)
                }
            }
        }
    }

    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val currentPath by viewModel.currentDirectoryPath.collectAsState()
    val rootPath = viewModel.repository.rootPath

    // --- Android Gesture & System Back Navigation Handlers ---
    BackHandler(enabled = showOpenWithFile != null) {
        showOpenWithFile = null
    }

    BackHandler(enabled = viewingFile != null && showOpenWithFile == null) {
        viewingFile = null
    }

    BackHandler(enabled = showCloudConnectDialog && viewingFile == null && showOpenWithFile == null) {
        showCloudConnectDialog = false
    }

    BackHandler(enabled = showPermissionDialog && !showCloudConnectDialog && viewingFile == null && showOpenWithFile == null) {
        showPermissionDialog = false
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    BackHandler(enabled = selectedCategory != null && !drawerState.isOpen && viewingFile == null && showOpenWithFile == null) {
        viewModel.closeCategory()
    }

    BackHandler(enabled = isSelectionMode && selectedCategory == null && !drawerState.isOpen && viewingFile == null && showOpenWithFile == null) {
        viewModel.clearSelection()
    }

    val isSubfolderInBrowse = currentDestination == NavDestination.BROWSE &&
            selectedCategory == null &&
            !isSelectionMode &&
            !drawerState.isOpen &&
            viewingFile == null &&
            showOpenWithFile == null &&
            currentPath != rootPath &&
            java.io.File(currentPath).parent != null

    BackHandler(enabled = isSubfolderInBrowse) {
        viewModel.navigateUpDirectory()
    }

    val isNotOnHome = currentDestination != NavDestination.HOME &&
            selectedCategory == null &&
            !isSelectionMode &&
            !drawerState.isOpen &&
            viewingFile == null &&
            showOpenWithFile == null &&
            !showCloudConnectDialog &&
            !showPermissionDialog &&
            !isSubfolderInBrowse

    BackHandler(enabled = isNotOnHome) {
        viewModel.navigateTo(NavDestination.HOME)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            NavigationDrawerContent(
                viewModel = viewModel,
                onDestinationSelected = { dest ->
                    viewModel.navigateTo(dest)
                },
                onCloseDrawer = {
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                // Bottom Navigation Bar
                NavigationBar(
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    val isHomeSelected = currentDestination == NavDestination.HOME && selectedCategory == null
                    NavigationBarItem(
                        selected = isHomeSelected,
                        onClick = {
                            viewModel.closeCategory()
                            viewModel.navigateTo(NavDestination.HOME)
                        },
                        icon = {
                            AnimatedNavIcon(
                                selectedIcon = Icons.Filled.Home,
                                unselectedIcon = Icons.Outlined.Home,
                                isSelected = isHomeSelected,
                                contentDescription = "Home"
                            )
                        },
                        label = { Text("Home") },
                        modifier = Modifier.testTag("nav_item_home")
                    )

                    val isBrowseSelected = currentDestination == NavDestination.BROWSE && selectedCategory == null
                    NavigationBarItem(
                        selected = isBrowseSelected,
                        onClick = {
                            viewModel.closeCategory()
                            viewModel.navigateTo(NavDestination.BROWSE)
                        },
                        icon = {
                            AnimatedNavIcon(
                                selectedIcon = Icons.Filled.Folder,
                                unselectedIcon = Icons.Outlined.FolderOpen,
                                isSelected = isBrowseSelected,
                                contentDescription = "Browse"
                            )
                        },
                        label = { Text("Browse") },
                        modifier = Modifier.testTag("nav_item_browse")
                    )

                    val isSearchSelected = currentDestination == NavDestination.SEARCH && selectedCategory == null
                    NavigationBarItem(
                        selected = isSearchSelected,
                        onClick = {
                            viewModel.closeCategory()
                            viewModel.navigateTo(NavDestination.SEARCH)
                        },
                        icon = {
                            AnimatedNavIcon(
                                selectedIcon = Icons.Filled.Search,
                                unselectedIcon = Icons.Outlined.Search,
                                isSelected = isSearchSelected,
                                contentDescription = "Search"
                            )
                        },
                        label = { Text("Search") },
                        modifier = Modifier.testTag("nav_item_search")
                    )

                    val isTagsSelected = currentDestination == NavDestination.TAGS && selectedCategory == null
                    NavigationBarItem(
                        selected = isTagsSelected,
                        onClick = {
                            viewModel.closeCategory()
                            viewModel.navigateTo(NavDestination.TAGS)
                        },
                        icon = {
                            AnimatedNavIcon(
                                selectedIcon = Icons.Filled.Label,
                                unselectedIcon = Icons.Outlined.Label,
                                isSelected = isTagsSelected,
                                contentDescription = "Tags"
                            )
                        },
                        label = { Text("Tags") },
                        modifier = Modifier.testTag("nav_item_tags")
                    )

                    val isSettingsSelected = currentDestination == NavDestination.SETTINGS && selectedCategory == null
                    NavigationBarItem(
                        selected = isSettingsSelected,
                        onClick = {
                            viewModel.closeCategory()
                            viewModel.navigateTo(NavDestination.SETTINGS)
                        },
                        icon = {
                            AnimatedNavIcon(
                                selectedIcon = Icons.Filled.Settings,
                                unselectedIcon = Icons.Outlined.Settings,
                                isSelected = isSettingsSelected,
                                contentDescription = "Settings"
                            )
                        },
                        label = { Text("Settings") },
                        modifier = Modifier.testTag("nav_item_settings")
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (selectedCategory != null) {
                    CategoryDetailScreen(
                        category = selectedCategory!!,
                        viewModel = viewModel,
                        onBack = { viewModel.closeCategory() },
                        onFileClick = { file -> viewingFile = file }
                    )
                } else {
                    Crossfade(targetState = currentDestination, label = "screen_transition") { dest ->
                        when (dest) {
                            NavDestination.HOME -> HomeScreen(
                                viewModel = viewModel,
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onOpenCategory = { cat -> viewModel.openCategory(cat) },
                                onOpenRecent = { viewModel.navigateTo(NavDestination.RECENT) },
                                onOpenStarred = { viewModel.navigateTo(NavDestination.TAGS) },
                                onOpenSafeFolder = { viewModel.navigateTo(NavDestination.SAFE_FOLDER) },
                                onOpenCloudConnect = { showCloudConnectDialog = true },
                                onFileClick = { file -> viewingFile = file }
                            )

                            NavDestination.BROWSE -> BrowseScreen(
                                viewModel = viewModel,
                                onFileClick = { file -> viewingFile = file }
                            )

                            NavDestination.SEARCH -> SearchScreen(
                                viewModel = viewModel,
                                onFileClick = { file -> viewingFile = file }
                            )

                            NavDestination.TAGS -> TagsScreen(
                                viewModel = viewModel,
                                onFileClick = { file -> viewingFile = file }
                            )

                            NavDestination.RECENT -> RecentFilesScreen(
                                viewModel = viewModel,
                                onFileClick = { file -> viewingFile = file }
                            )

                            NavDestination.BIN -> BinScreen(
                                viewModel = viewModel,
                                onFileClick = { file -> viewingFile = file }
                            )

                            NavDestination.SETTINGS -> SettingsScreen(
                                viewModel = viewModel
                            )

                            NavDestination.SAFE_FOLDER -> SafeFolderScreen(
                                viewModel = viewModel,
                                onBack = { viewModel.navigateTo(NavDestination.HOME) },
                                onFileClick = { file -> viewingFile = file }
                            )
                        }
                    }
                }
            }
        }
    }

    // Permission Rationale Dialog
    if (showPermissionDialog) {
        PermissionRationaleDialog(
            onDismiss = { showPermissionDialog = false },
            onGrantClick = {
                showPermissionDialog = false
                onRequestAllFilesPermission()
            }
        )
    }

    // Cloud Connect Dialog
    if (showCloudConnectDialog) {
        CloudConnectDialog(
            onDismiss = { showCloudConnectDialog = false },
            onConnectProvider = { provider ->
                showCloudConnectDialog = false
                viewModel.connectCloudAccount(provider)
            }
        )
    }

    // File Preview / Content Dialog
    viewingFile?.let { file ->
        FileViewerDialog(
            file = file,
            onDismiss = { viewingFile = null },
            onExtractZip = { zipFile ->
                viewModel.performExtractZip(zipFile.path)
            },
            onOpenWith = { openFile ->
                showOpenWithFile = openFile
            }
        )
    }

    // Open with Intent-Based App Selector Sheet
    val openWithTarget = showOpenWithFile
    if (openWithTarget != null) {
        OpenWithBottomSheet(
            file = openWithTarget,
            onDismiss = { showOpenWithFile = null },
            onOpenWithBuiltInViewer = {
                showOpenWithFile = null
                viewingFile = openWithTarget
            },
            onOpenSuccess = { msg ->
                viewModel.emitSnackbar(msg)
            }
        )
    }

    // ZIP Progress & Cancellation Overlay
    zipProgress?.let { progress ->
        ZipProgressDialog(
            progress = progress,
            onCancel = { viewModel.cancelZipOperation() }
        )
    }
}
