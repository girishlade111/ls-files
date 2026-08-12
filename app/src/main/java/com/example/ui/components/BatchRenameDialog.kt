package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.FileItem

enum class BatchRenameMode(val title: String) {
    PREFIX_SUFFIX("Prefix / Suffix"),
    FIND_REPLACE("Find & Replace"),
    NUMBERING("Sequential Numbers")
}

data class BatchRenameOptions(
    val mode: BatchRenameMode = BatchRenameMode.PREFIX_SUFFIX,
    val prefix: String = "",
    val suffix: String = "",
    val replaceBaseName: Boolean = false,
    val newBaseName: String = "",
    val findText: String = "",
    val replaceText: String = "",
    val isCaseSensitive: Boolean = false,
    val addNumbering: Boolean = true,
    val numberStart: Int = 1,
    val numberPadding: Int = 2,
    val preserveExtension: Boolean = true
)

fun generateNewFileName(file: FileItem, index: Int, options: BatchRenameOptions): String {
    val originalName = file.name
    val isDirectory = file.isDirectory

    val (baseName, extension) = if (isDirectory || !options.preserveExtension) {
        Pair(originalName, "")
    } else {
        if (originalName.contains(".")) {
            Pair(originalName.substringBeforeLast("."), "." + originalName.substringAfterLast("."))
        } else {
            Pair(originalName, "")
        }
    }

    var resultBase = when (options.mode) {
        BatchRenameMode.PREFIX_SUFFIX -> {
            val name = if (options.replaceBaseName && options.newBaseName.isNotBlank()) {
                options.newBaseName
            } else {
                baseName
            }
            "${options.prefix}$name${options.suffix}"
        }
        BatchRenameMode.FIND_REPLACE -> {
            if (options.findText.isNotEmpty()) {
                if (options.isCaseSensitive) {
                    baseName.replace(options.findText, options.replaceText)
                } else {
                    baseName.replace(
                        Regex(Regex.escape(options.findText), RegexOption.IGNORE_CASE),
                        options.replaceText
                    )
                }
            } else {
                baseName
            }
        }
        BatchRenameMode.NUMBERING -> {
            if (options.replaceBaseName && options.newBaseName.isNotBlank()) {
                options.newBaseName
            } else {
                baseName
            }
        }
    }

    if (options.addNumbering && options.mode != BatchRenameMode.FIND_REPLACE) {
        val num = options.numberStart + index
        val numFormatted = String.format("%0${options.numberPadding.coerceAtLeast(1)}d", num)
        resultBase = if (resultBase.isNotEmpty()) "${resultBase}_$numFormatted" else numFormatted
    }

    return "$resultBase$extension"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchRenameDialog(
    selectedFiles: List<FileItem>,
    onDismiss: () -> Unit,
    onApplyBatchRename: (List<Pair<String, String>>) -> Unit
) {
    var mode by remember { mutableStateOf(BatchRenameMode.PREFIX_SUFFIX) }
    var prefix by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    var replaceBaseName by remember { mutableStateOf(false) }
    var newBaseName by remember { mutableStateOf("") }

    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var isCaseSensitive by remember { mutableStateOf(false) }

    var addNumbering by remember { mutableStateOf(selectedFiles.size > 1) }
    var numberStartText by remember { mutableStateOf("1") }
    var numberDigits by remember { mutableStateOf(2) } // 2 -> 01, 02

    var preserveExtension by remember { mutableStateOf(true) }

    val options = remember(
        mode, prefix, suffix, replaceBaseName, newBaseName,
        findText, replaceText, isCaseSensitive,
        addNumbering, numberStartText, numberDigits, preserveExtension
    ) {
        BatchRenameOptions(
            mode = mode,
            prefix = prefix,
            suffix = suffix,
            replaceBaseName = replaceBaseName,
            newBaseName = newBaseName,
            findText = findText,
            replaceText = replaceText,
            isCaseSensitive = isCaseSensitive,
            addNumbering = addNumbering,
            numberStart = numberStartText.toIntOrNull() ?: 1,
            numberPadding = numberDigits,
            preserveExtension = preserveExtension
        )
    }

    val previewList = remember(selectedFiles, options) {
        selectedFiles.mapIndexed { index, file ->
            val newName = generateNewFileName(file, index, options)
            Triple(file, file.name, newName)
        }
    }

    val renamesToApply = remember(previewList) {
        previewList.map { (file, _, newName) ->
            Pair(file.path, newName)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(16.dp)
                .testTag("batch_rename_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                // Dialog Title Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.semantics { heading() }) {
                        Text(
                            text = "Batch Rename (${selectedFiles.size} Files)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Configure pattern and preview live names below",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Mode Tabs
                SecondaryTabRow(
                    selectedTabIndex = mode.ordinal,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BatchRenameMode.values().forEach { item ->
                        Tab(
                            selected = mode == item,
                            onClick = { mode = item },
                            text = {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (mode == item) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Pattern Configuration Options Box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(14.dp)
                ) {
                    when (mode) {
                        BatchRenameMode.PREFIX_SUFFIX -> {
                            OutlinedTextField(
                                value = prefix,
                                onValueChange = { prefix = it },
                                label = { Text("Prefix (added to start)") },
                                placeholder = { Text("e.g., IMG_ or Doc_") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("batch_rename_prefix_input")
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = suffix,
                                onValueChange = { suffix = it },
                                label = { Text("Suffix (added to end)") },
                                placeholder = { Text("e.g., _v2 or _edited") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("batch_rename_suffix_input")
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = replaceBaseName,
                                    onCheckedChange = { replaceBaseName = it }
                                )
                                Text(
                                    text = "Replace base file name",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            AnimatedVisibility(visible = replaceBaseName) {
                                OutlinedTextField(
                                    value = newBaseName,
                                    onValueChange = { newBaseName = it },
                                    label = { Text("New Base Name") },
                                    placeholder = { Text("e.g., Document") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp)
                                )
                            }
                        }

                        BatchRenameMode.FIND_REPLACE -> {
                            OutlinedTextField(
                                value = findText,
                                onValueChange = { findText = it },
                                label = { Text("Find Text") },
                                placeholder = { Text("e.g., IMG") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = replaceText,
                                onValueChange = { replaceText = it },
                                label = { Text("Replace With") },
                                placeholder = { Text("e.g., Photo") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = isCaseSensitive,
                                    onCheckedChange = { isCaseSensitive = it }
                                )
                                Text(
                                    text = "Match case",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        BatchRenameMode.NUMBERING -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = replaceBaseName,
                                    onCheckedChange = { replaceBaseName = it }
                                )
                                Text(
                                    text = "Replace base name with custom text",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            if (replaceBaseName) {
                                OutlinedTextField(
                                    value = newBaseName,
                                    onValueChange = { newBaseName = it },
                                    label = { Text("Base Name") },
                                    placeholder = { Text("e.g., Report") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = numberStartText,
                                    onValueChange = { if (it.all { c -> c.isDigit() }) numberStartText = it },
                                    label = { Text("Start From") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )

                                FilterChip(
                                    selected = numberDigits == 2,
                                    onClick = { numberDigits = if (numberDigits == 2) 1 else 2 },
                                    label = { Text(if (numberDigits == 2) "01, 02..." else "1, 2...") },
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Common Toggles
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (mode != BatchRenameMode.NUMBERING) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = addNumbering,
                                    onCheckedChange = { addNumbering = it }
                                )
                                Text(
                                    text = "Append index (01, 02)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = preserveExtension,
                                onCheckedChange = { preserveExtension = it }
                            )
                            Text(
                                text = "Keep extensions (.jpg)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Live Rename Preview List Header
                Text(
                    text = "Live Preview",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Scrollable Live Preview Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(previewList) { idx, (_, oldName, newName) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${idx + 1}.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(22.dp)
                                )
                                Text(
                                    text = oldName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Outlined.ArrowForward,
                                    contentDescription = "renames to",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(horizontal = 6.dp)
                                        .size(14.dp)
                                )
                                Text(
                                    text = newName,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Dialog Buttons
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onApplyBatchRename(renamesToApply) },
                        enabled = renamesToApply.any { (_, newName) -> newName.isNotBlank() },
                        modifier = Modifier.testTag("apply_batch_rename_button")
                    ) {
                        Text("Apply Rename")
                    }
                }
            }
        }
    }
}
