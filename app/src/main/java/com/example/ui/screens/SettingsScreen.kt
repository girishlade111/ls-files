package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel
import com.example.ui.components.AnimatedIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit = {},
    onOpenBin: () -> Unit = {},
    onOpenSafeFolder: () -> Unit = {}
) {
    val context = LocalContext.current
    val showHiddenFiles by viewModel.showHiddenFiles.collectAsState()
    val smartSearchEnabled by viewModel.smartSearchEnabled.collectAsState()
    val pauseSearchHistory by viewModel.pauseSearchHistory.collectAsState()

    var showCleanSheet by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf("English (US)") }
    var showAboutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    AnimatedIconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_btn")) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // General / Language Section
            SettingsSectionHeader("General")
            SettingsClickableRow(
                icon = Icons.Outlined.Language,
                title = "Language",
                subtitle = selectedLanguage,
                onClick = { showLanguagePicker = true },
                testTag = "language_setting_row"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Search Section
            SettingsSectionHeader("Search")
            SettingsToggleRow(
                icon = Icons.Outlined.AutoAwesome,
                title = "Smart Search",
                subtitle = "On-device search in documents & image OCR text",
                checked = smartSearchEnabled,
                onCheckedChange = { viewModel.toggleSmartSearch(it) }
            )
            SettingsToggleRow(
                icon = Icons.Outlined.PauseCircleOutline,
                title = "Pause search history",
                subtitle = "Temporarily stop recording search queries",
                checked = pauseSearchHistory,
                onCheckedChange = { viewModel.togglePauseSearchHistory(it) }
            )
            SettingsClickableRow(
                icon = Icons.Outlined.DeleteSweep,
                title = "Clear search history",
                subtitle = "Delete all saved search queries",
                onClick = { viewModel.clearSearchHistory() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Clean Section
            SettingsSectionHeader("Clean")
            SettingsClickableRow(
                icon = Icons.Outlined.CleaningServices,
                title = "Clean suggestions",
                subtitle = "Duplicate finder & junk cleaner",
                onClick = { showCleanSheet = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Browse Section
            SettingsSectionHeader("Browse")
            SettingsToggleRow(
                icon = Icons.Outlined.Visibility,
                title = "Show hidden files",
                subtitle = "Display dotfiles (.nomedia, .git) in directory tree",
                checked = showHiddenFiles,
                onCheckedChange = { viewModel.toggleShowHiddenFiles(it) }
            )
            SettingsClickableRow(
                icon = Icons.Outlined.Lock,
                title = "Safe folder",
                subtitle = "Encrypted PIN-protected storage",
                onClick = onOpenSafeFolder,
                testTag = "safe_folder_setting_row"
            )
            SettingsClickableRow(
                icon = Icons.Outlined.Delete,
                title = "Bin / Recently deleted",
                subtitle = "Recover soft-deleted items within 30 days",
                onClick = onOpenBin
            )

            Spacer(modifier = Modifier.height(16.dp))

            // System & Permissions
            SettingsSectionHeader("Permissions & Storage")
            val hasAllFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else true

            SettingsClickableRow(
                icon = Icons.Outlined.Security,
                title = "Storage permission status",
                subtitle = if (hasAllFilesAccess) "All Files Access Granted" else "Tap to grant All Files Access",
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // About
            SettingsSectionHeader("About")
            SettingsClickableRow(
                icon = Icons.Outlined.Info,
                title = "About LS Files",
                subtitle = "Version 1.0.0 · LadeStack Products",
                onClick = { showAboutDialog = true }
            )
        }
    }

    // Clean coming soon sheet
    if (showCleanSheet) {
        ModalBottomSheet(onDismissRequest = { showCleanSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Icon(
                    Icons.Outlined.CleaningServices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Storage Cleaner Engine",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The intelligent storage cleaner, duplicate detector, and cache optimizer engine will arrive in LS Files v1.1 update.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { showCleanSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Got it")
                }
            }
        }
    }

    // Language Dialog
    if (showLanguagePicker) {
        val languages = listOf("English (US)", "Spanish", "French", "German", "Japanese", "Portuguese")
        AlertDialog(
            onDismissRequest = { showLanguagePicker = false },
            title = { Text("Select Display Language") },
            text = {
                Column {
                    languages.forEach { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedLanguage = lang
                                    showLanguagePicker = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedLanguage == lang,
                                onClick = {
                                    selectedLanguage = lang
                                    showLanguagePicker = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(lang, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguagePicker = false }) {
                    Text("Close")
                }
            }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("LS Files") },
            text = {
                Column {
                    Text("LS Files v1.0.0", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Enterprise-grade on-device + cloud file manager by LadeStack. Built with Material Design 3, Room, and Jetpack Compose.")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No app-level accounts required. Free, open source, no ads, no paywalls.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
fun SettingsClickableRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String? = null
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
