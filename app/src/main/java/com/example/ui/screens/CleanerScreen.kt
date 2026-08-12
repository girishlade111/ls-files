package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FileItem
import com.example.data.util.*
import com.example.ui.util.formatSize
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CleanerScreen(
    onBack: () -> Unit,
    onCleanJunk: (List<File>) -> Unit,
    onDeleteDuplicates: (List<File>) -> Unit,
    onDeleteLargeFiles: (List<FileItem>) -> Unit
) {
    var selectedTab by remember { java.util.concurrent.atomic.AtomicInteger(0).let { mutableIntStateOf(0) } }
    val cleanerManager = remember { StorageCleanerManager() }

    var isScanningJunk by remember { mutableStateOf(false) }
    var junkItems by remember { mutableStateOf<List<JunkItem>>(emptyList()) }
    var selectedJunkPaths by remember { mutableStateOf<Set<String>>(emptySet()) }

    var isScanningDuplicates by remember { mutableStateOf(false) }
    var duplicateGroups by remember { mutableStateOf<List<DuplicateGroup>>(emptyList()) }
    var selectedDuplicatePaths by remember { mutableStateOf<Set<String>>(emptySet()) }

    var isScanningLargeFiles by remember { mutableStateOf(false) }
    var largeFiles by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var selectedLargeFilePaths by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Initial scans
    LaunchedEffect(Unit) {
        isScanningJunk = true
        junkItems = cleanerManager.scanJunkFiles()
        selectedJunkPaths = junkItems.map { it.file.absolutePath }.toSet()
        isScanningJunk = false

        isScanningDuplicates = true
        duplicateGroups = cleanerManager.scanDuplicateFiles()
        // Auto select duplicate copies (keeping the first one in each group)
        val autoSelected = mutableSetOf<String>()
        duplicateGroups.forEach { group ->
            group.files.drop(1).forEach { autoSelected.add(it.path) }
        }
        selectedDuplicatePaths = autoSelected
        isScanningDuplicates = false

        isScanningLargeFiles = true
        largeFiles = cleanerManager.scanLargeFiles(minSizeMB = 50)
        isScanningLargeFiles = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage Cleaner & Optimizer", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Junk Cleaner") },
                    icon = { Icon(Icons.Default.CleaningServices, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Duplicates") },
                    icon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Large Files") },
                    icon = { Icon(Icons.Default.FolderZip, contentDescription = null) }
                )
            }

            when (selectedTab) {
                0 -> {
                    JunkCleanerTab(
                        isScanning = isScanningJunk,
                        junkItems = junkItems,
                        selectedPaths = selectedJunkPaths,
                        onToggleSelectPath = { path ->
                            selectedJunkPaths = if (selectedJunkPaths.contains(path)) {
                                selectedJunkPaths - path
                            } else {
                                selectedJunkPaths + path
                            }
                        },
                        onClean = {
                            val toClean = junkItems.filter { selectedJunkPaths.contains(it.file.absolutePath) }.map { it.file }
                            onCleanJunk(toClean)
                            junkItems = junkItems.filterNot { selectedJunkPaths.contains(it.file.absolutePath) }
                            selectedJunkPaths = emptySet()
                        }
                    )
                }
                1 -> {
                    DuplicateFinderTab(
                        isScanning = isScanningDuplicates,
                        groups = duplicateGroups,
                        selectedPaths = selectedDuplicatePaths,
                        onToggleSelectPath = { path ->
                            selectedDuplicatePaths = if (selectedDuplicatePaths.contains(path)) {
                                selectedDuplicatePaths - path
                            } else {
                                selectedDuplicatePaths + path
                            }
                        },
                        onDelete = {
                            val toDelete = duplicateGroups.flatMap { g -> g.files }.filter { selectedDuplicatePaths.contains(it.path) }.map { File(it.path) }
                            onDeleteDuplicates(toDelete)
                            duplicateGroups = duplicateGroups.mapNotNull { g ->
                                val remaining = g.files.filterNot { selectedDuplicatePaths.contains(it.path) }
                                if (remaining.size > 1) g.copy(files = remaining) else null
                            }
                            selectedDuplicatePaths = emptySet()
                        }
                    )
                }
                2 -> {
                    LargeFilesTab(
                        isScanning = isScanningLargeFiles,
                        files = largeFiles,
                        selectedPaths = selectedLargeFilePaths,
                        onToggleSelectPath = { path ->
                            selectedLargeFilePaths = if (selectedLargeFilePaths.contains(path)) {
                                selectedLargeFilePaths - path
                            } else {
                                selectedLargeFilePaths + path
                            }
                        },
                        onDelete = {
                            val toDelete = largeFiles.filter { selectedLargeFilePaths.contains(it.path) }
                            onDeleteLargeFiles(toDelete)
                            largeFiles = largeFiles.filterNot { selectedLargeFilePaths.contains(it.path) }
                            selectedLargeFilePaths = emptySet()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun JunkCleanerTab(
    isScanning: Boolean,
    junkItems: List<JunkItem>,
    selectedPaths: Set<String>,
    onToggleSelectPath: (String) -> Unit,
    onClean: () -> Unit
) {
    val totalSelectedSize = junkItems.filter { selectedPaths.contains(it.file.absolutePath) }.sumOf { it.size }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isScanning) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Scanning storage for junk files...")
            }
        } else if (junkItems.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Storage is Clean!", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("No temp files or junk found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Selected Junk", fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(
                                formatSize(totalSelectedSize),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Button(
                            onClick = onClean,
                            enabled = selectedPaths.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clean Now")
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(junkItems) { item ->
                        val path = item.file.absolutePath
                        val isSelected = selectedPaths.contains(path)
                        ListItem(
                            headlineContent = { Text(item.file.name, fontWeight = FontWeight.Medium) },
                            supportingContent = { Text("${item.category.name} • ${path}", fontSize = 12.sp, maxLines = 1) },
                            trailingContent = {
                                Text(formatSize(item.size), fontWeight = FontWeight.Bold)
                            },
                            leadingContent = {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onToggleSelectPath(path) }
                                )
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun DuplicateFinderTab(
    isScanning: Boolean,
    groups: List<DuplicateGroup>,
    selectedPaths: Set<String>,
    onToggleSelectPath: (String) -> Unit,
    onDelete: () -> Unit
) {
    val totalSelectedSize = groups.flatMap { it.files }
        .filter { selectedPaths.contains(it.path) }
        .sumOf { it.size }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isScanning) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Analyzing duplicate files...")
            }
        } else if (groups.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.FilterNone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("No Duplicate Files Found", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Reclaim Space", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(
                                formatSize(totalSelectedSize),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Button(
                            onClick = onDelete,
                            enabled = selectedPaths.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete Duplicates")
                        }
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    groups.forEachIndexed { groupIndex, group ->
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Group #${groupIndex + 1} (${group.files.size} copies)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(formatSize(group.fileSize) + " each", fontSize = 13.sp)
                                }
                            }
                        }
                        items(group.files) { file ->
                            val isSelected = selectedPaths.contains(file.path)
                            ListItem(
                                headlineContent = { Text(file.name, maxLines = 1) },
                                supportingContent = { Text(file.path, fontSize = 11.sp, maxLines = 1) },
                                leadingContent = {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { onToggleSelectPath(file.path) }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LargeFilesTab(
    isScanning: Boolean,
    files: List<FileItem>,
    selectedPaths: Set<String>,
    onToggleSelectPath: (String) -> Unit,
    onDelete: () -> Unit
) {
    val totalSelectedSize = files.filter { selectedPaths.contains(it.path) }.sumOf { it.size }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isScanning) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Searching for files > 50MB...")
            }
        } else if (files.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.FolderZip, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("No Large Files Found", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Selected Large Files", fontSize = 14.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text(
                                formatSize(totalSelectedSize),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Button(
                            onClick = onDelete,
                            enabled = selectedPaths.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete Selected")
                        }
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(files) { file ->
                        val isSelected = selectedPaths.contains(file.path)
                        ListItem(
                            headlineContent = { Text(file.name, fontWeight = FontWeight.Medium, maxLines = 1) },
                            supportingContent = { Text(file.path, fontSize = 11.sp, maxLines = 1) },
                            trailingContent = { Text(formatSize(file.size), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
                            leadingContent = {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onToggleSelectPath(file.path) }
                                )
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}
