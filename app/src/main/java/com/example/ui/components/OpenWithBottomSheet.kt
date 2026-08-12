package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.model.FileItem
import java.io.File

data class AppHandlerInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable?,
    val isDefault: Boolean = false
)

private fun Drawable.toImageBitmap(): androidx.compose.ui.graphics.ImageBitmap {
    if (this is BitmapDrawable && this.bitmap != null) {
        return this.bitmap.asImageBitmap()
    }
    val w = if (intrinsicWidth > 0) intrinsicWidth else 96
    val h = if (intrinsicHeight > 0) intrinsicHeight else 96
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}

fun resolveFileMimeType(fileItem: FileItem): String {
    val file = File(fileItem.path)
    val ext = file.extension.lowercase()
    val mapMime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    if (!mapMime.isNullOrBlank()) return mapMime

    if (fileItem.mimeType.isNotBlank() && fileItem.mimeType != "application/octet-stream" && fileItem.mimeType != "*/*") {
        return fileItem.mimeType
    }

    return when (ext) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        "svg" -> "image/svg+xml"

        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "3gp" -> "video/3gpp"
        "flv" -> "video/x-flv"
        "wmv" -> "video/x-ms-wmv"

        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "aac" -> "audio/aac"
        "flac" -> "audio/flac"
        "m4a" -> "audio/mp4"
        "ogg" -> "audio/ogg"

        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "rtf" -> "application/rtf"

        "txt", "log" -> "text/plain"
        "html", "htm" -> "text/html"
        "json" -> "application/json"
        "xml" -> "text/xml"
        "csv" -> "text/csv"
        "md" -> "text/markdown"
        "js" -> "text/javascript"
        "css" -> "text/css"

        "apk" -> "application/vnd.android.package-archive"
        "zip" -> "application/zip"
        "rar" -> "application/x-rar-compressed"
        "7z" -> "application/x-7z-compressed"
        "tar" -> "application/x-tar"
        "gz" -> "application/gzip"

        else -> "*/*"
    }
}

fun getFileContentUri(context: Context, filePath: String): Uri {
    val file = File(filePath)
    return try {
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    } catch (e: Exception) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
                val builder = android.os.StrictMode.VmPolicy.Builder()
                android.os.StrictMode.setVmPolicy(builder.build())
            } catch (_: Exception) {}
        }
        Uri.fromFile(file)
    }
}

fun queryAppsForFile(context: Context, fileItem: FileItem): Pair<List<AppHandlerInfo>, Intent> {
    val mimeType = resolveFileMimeType(fileItem)
    val uri = getFileContentUri(context, fileItem.path)

    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    val pm = context.packageManager
    var resolvedList = pm.queryIntentActivities(
        viewIntent,
        PackageManager.MATCH_DEFAULT_ONLY
    )
    if (resolvedList.isEmpty()) {
        resolvedList = pm.queryIntentActivities(viewIntent, 0)
    }

    if (resolvedList.isEmpty() && mimeType != "*/*") {
        val genericType = when {
            mimeType.startsWith("image/") -> "image/*"
            mimeType.startsWith("video/") -> "video/*"
            mimeType.startsWith("audio/") -> "audio/*"
            mimeType.startsWith("text/") -> "text/*"
            else -> "*/*"
        }
        val genericIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, genericType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        resolvedList = pm.queryIntentActivities(genericIntent, PackageManager.MATCH_DEFAULT_ONLY).ifEmpty {
            pm.queryIntentActivities(genericIntent, 0)
        }
    }

    val defaultResolve = pm.resolveActivity(viewIntent, PackageManager.MATCH_DEFAULT_ONLY)
    val defaultPackage = defaultResolve?.activityInfo?.packageName

    val myPackageName = context.packageName
    val apps = resolvedList
        .filter { it.activityInfo.packageName != myPackageName }
        .map { resolveInfo ->
            val label = resolveInfo.loadLabel(pm).toString()
            val icon = try { resolveInfo.loadIcon(pm) } catch (e: Exception) { null }
            val pkg = resolveInfo.activityInfo.packageName
            val act = resolveInfo.activityInfo.name
            AppHandlerInfo(
                label = label,
                packageName = pkg,
                activityName = act,
                icon = icon,
                isDefault = (pkg == defaultPackage)
            )
        }.distinctBy { "${it.packageName}/${it.activityName}" }

    return Pair(apps, viewIntent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenWithBottomSheet(
    file: FileItem,
    onDismiss: () -> Unit,
    onOpenWithBuiltInViewer: (() -> Unit)? = null,
    onOpenSuccess: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val mimeType = remember(file) { resolveFileMimeType(file) }
    val (appsList, baseIntent) = remember(file) { queryAppsForFile(context, file) }

    fun launchApp(app: AppHandlerInfo) {
        try {
            val uri = getFileContentUri(context, file.path)
            val launchIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setClassName(app.packageName, app.activityName)
            }
            context.startActivity(launchIntent)
            onOpenSuccess?.invoke("Opened with ${app.label}")
            onDismiss()
        } catch (e: Exception) {
            // Fallback to system chooser if direct launch fails
            try {
                val chooser = Intent.createChooser(baseIntent, "Open '${file.name}' with")
                context.startActivity(chooser)
                onOpenSuccess?.invoke("Opened system app selector")
                onDismiss()
            } catch (ex: Exception) {
                onOpenSuccess?.invoke("Failed to open app: ${ex.localizedMessage}")
            }
        }
    }

    fun launchSystemChooser() {
        try {
            val chooser = Intent.createChooser(baseIntent, "Open '${file.name}' with")
            context.startActivity(chooser)
            onOpenSuccess?.invoke("Opened system chooser")
            onDismiss()
        } catch (e: Exception) {
            onOpenSuccess?.invoke("Could not launch chooser: ${e.localizedMessage}")
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("open_with_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp)
        ) {
            // Header Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Open '${file.name}' with",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = mimeType,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = if (file.isDirectory) "${file.childCount} items" else com.example.ui.components.formatFileSize(file.sizeBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            // System App Chooser Row (Always Available)
            ListItem(
                headlineContent = {
                    Text(
                        "System App Chooser",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                },
                supportingContent = {
                    Text(
                        "Use Android system default dialog to select an app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.Apps,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                },
                trailingContent = {
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier
                    .clickable { launchSystemChooser() }
                    .testTag("open_with_system_chooser_btn")
            )

            if (onOpenWithBuiltInViewer != null) {
                ListItem(
                    headlineContent = {
                        Text(
                            "Built-in File Preview",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                        )
                    },
                    supportingContent = {
                        Text(
                            "View file contents directly inside LsFiles app",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.Visibility,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    },
                    trailingContent = {
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .clickable {
                            onDismiss()
                            onOpenWithBuiltInViewer()
                        }
                        .testTag("open_with_builtin_viewer_btn")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle for dynamically populated handler apps
            Text(
                text = if (appsList.isNotEmpty()) "Available Compatible Apps (${appsList.size})" else "Compatible Installed Apps",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
            )

            if (appsList.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.FindInPage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No specialized external apps registered for MIME type '$mimeType'",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { launchSystemChooser() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Launch, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Try System Handler Chooser")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(appsList, key = { "${it.packageName}/${it.activityName}" }) { app ->
                        ListItem(
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (app.isDefault) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "Default",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            supportingContent = {
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = {
                                if (app.icon != null) {
                                    Image(
                                        bitmap = app.icon.toImageBitmap(),
                                        contentDescription = app.label,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Outlined.Android,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            trailingContent = {
                                Icon(
                                    Icons.Outlined.Launch,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            modifier = Modifier
                                .clickable { launchApp(app) }
                                .testTag("open_with_app_item_${app.packageName}")
                        )
                    }
                }
            }
        }
    }
}
