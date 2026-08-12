package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.db.TagEntity
import com.example.data.model.FileItem
import com.example.ui.MainViewModel
import com.example.ui.components.AnimatedIconButton
import com.example.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    viewModel: MainViewModel,
    onFileClick: (FileItem) -> Unit = {}
) {
    val tags by viewModel.tagsFlow.collectAsState(initial = emptyList())
    var selectedTagId by remember { mutableStateOf<Long?>(null) }
    var showCreateTagDialog by remember { mutableStateOf(false) }

    val hasTagsState = showCreateTagDialog || selectedTagId != null

    BackHandler(enabled = hasTagsState) {
        if (showCreateTagDialog) {
            showCreateTagDialog = false
        } else {
            selectedTagId = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "File Tags",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
            AnimatedIconButton(
                onClick = { showCreateTagDialog = true },
                modifier = Modifier.testTag("create_tag_btn")
            ) {
                Icon(Icons.Outlined.AddCircleOutline, contentDescription = "Create Tag")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tag Chips Carousel
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = selectedTagId == null,
                    onClick = { selectedTagId = null },
                    label = { Text("All Tags") }
                )
            }
            items(tags, key = { it.tagId }) { tag ->
                val tagColor = parseColorHex(tag.tagColorHex)
                FilterChip(
                    selected = selectedTagId == tag.tagId,
                    onClick = { selectedTagId = tag.tagId },
                    label = { Text(tag.tagName) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(tagColor)
                        )
                    }
                )
            }
            item {
                FilterChip(
                    selected = selectedTagId == -1L,
                    onClick = { selectedTagId = -1L },
                    label = { Text("Unresolved") },
                    leadingIcon = { Icon(Icons.Outlined.Warning, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Selected Tag File List
        if (tags.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.LocalOffer,
                title = "No Custom Tags Created",
                description = "Tags let you categorize and instantly filter files across different directories without moving them.",
                actionText = "Create First Tag",
                onAction = { showCreateTagDialog = true },
                tipText = "Tip: Tagged files remain safe in their original folders while accessible here.",
                modifier = Modifier.weight(1f)
            )
        } else {
            val activeTag = tags.find { it.tagId == selectedTagId }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activeTag?.let { "Files tagged '${it.tagName}'" } ?: if (selectedTagId == -1L) "Unresolved Tags" else "Select a tag above to view tagged files",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                EmptyState(
                    icon = Icons.Outlined.BookmarkBorder,
                    title = activeTag?.let { "No Files Tagged '${it.tagName}'" } ?: "No Tagged Items Found",
                    description = "Assign tags to files from the Browse or Search screens using the file options menu.",
                    actionText = "Create New Tag",
                    onAction = { showCreateTagDialog = true },
                    tipText = "Tip: Open any file's options menu (⋮) in Browse and select 'Tags' to attach tags.",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (showCreateTagDialog) {
        CreateTagDialog(
            onDismiss = { showCreateTagDialog = false },
            onCreate = { name, colorHex ->
                showCreateTagDialog = false
                viewModel.createTag(name, colorHex)
            }
        )
    }
}

@Composable
fun CreateTagDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var tagName by remember { mutableStateOf("") }
    val colors = listOf("#F44336", "#2196F3", "#9C27B0", "#4CAF50", "#FF9800", "#00BCD4")
    var selectedColor by remember { mutableStateOf(colors.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Tag") },
        text = {
            Column {
                OutlinedTextField(
                    value = tagName,
                    onValueChange = { tagName = it },
                    label = { Text("Tag Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tag_name_input")
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Select Tag Color", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    colors.forEach { hex ->
                        val color = parseColorHex(hex)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColor = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColor == hex) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (tagName.isNotBlank()) onCreate(tagName.trim(), selectedColor)
                },
                modifier = Modifier.testTag("tag_submit_btn")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun parseColorHex(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color.Gray
    }
}
