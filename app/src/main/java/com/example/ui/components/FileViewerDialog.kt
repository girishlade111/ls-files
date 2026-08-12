package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.os.ParcelFileDescriptor
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.FileCategory
import com.example.data.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerDialog(
    file: FileItem,
    onDismiss: () -> Unit,
    onExtractZip: ((FileItem) -> Unit)? = null,
    onOpenWith: ((FileItem) -> Unit)? = null
) {
    val ext = remember(file.path) { File(file.path).extension.lowercase() }
    val mimeType = remember(file) { resolveFileMimeType(file) }

    val isImage = remember(mimeType, ext) {
        mimeType.startsWith("image/") || file.category == FileCategory.IMAGES || file.category == FileCategory.SCREENSHOTS ||
                listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "svg").contains(ext)
    }

    val isVideo = remember(mimeType, ext) {
        mimeType.startsWith("video/") || file.category == FileCategory.VIDEOS ||
                listOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "wmv", "m4v", "ts").contains(ext)
    }

    val isAudio = remember(mimeType, ext) {
        mimeType.startsWith("audio/") || file.category == FileCategory.AUDIO ||
                listOf("mp3", "wav", "aac", "flac", "m4a", "ogg", "wma", "opus").contains(ext)
    }

    val isPdf = remember(mimeType, ext) {
        mimeType == "application/pdf" || ext == "pdf"
    }

    val isTextOrCode = remember(mimeType, ext) {
        file.category == FileCategory.DOCUMENTS || mimeType.startsWith("text/") ||
                listOf("txt", "json", "xml", "csv", "md", "kt", "java", "py", "js", "html", "htm", "css", "log", "rtf").contains(ext)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar with Title, File Info and Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when {
                                        isImage -> Icons.Outlined.Image
                                        isVideo -> Icons.Outlined.PlayCircle
                                        isAudio -> Icons.Outlined.MusicNote
                                        isPdf -> Icons.Outlined.PictureAsPdf
                                        isTextOrCode -> Icons.Outlined.Description
                                        else -> Icons.Outlined.InsertDriveFile
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${formatFileSize(file.sizeBytes)} · $mimeType",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Open In Other Apps Button (Header Action)
                        if (onOpenWith != null) {
                            FilledTonalButton(
                                onClick = {
                                    onDismiss()
                                    onOpenWith(file)
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("viewer_header_open_with_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.OpenInNew,
                                    contentDescription = "Open in other apps",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open in other apps", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close")
                        }
                    }
                }

                // Main Viewer Body Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(
                            if (isImage || isVideo) Color.Black else MaterialTheme.colorScheme.surface
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isImage -> BuiltInImageViewer(file = file)
                        isVideo -> BuiltInVideoPlayer(file = file)
                        isAudio -> BuiltInAudioPlayer(file = file)
                        isPdf -> BuiltInPdfViewer(file = file)
                        isTextOrCode -> BuiltInTextViewer(file = file)
                        else -> GenericFilePreviewCard(file = file)
                    }
                }

                // Bottom Footer Action Controls
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (onOpenWith != null) {
                                Button(
                                    onClick = {
                                        onDismiss()
                                        onOpenWith(file)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.testTag("viewer_footer_open_with_btn")
                                ) {
                                    Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Open in other apps")
                                }
                            }

                            if ((ext == "zip" || file.category == FileCategory.ARCHIVES) && onExtractZip != null) {
                                FilledTonalButton(
                                    onClick = {
                                        onDismiss()
                                        onExtractZip(file)
                                    }
                                ) {
                                    Icon(Icons.Outlined.Unarchive, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Extract Zip")
                                }
                            }
                        }

                        OutlinedButton(onClick = onDismiss) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BuiltInImageViewer(file: FileItem) {
    var bitmap by remember(file.path) { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(file.path) {
        isLoading = true
        errorMsg = null
        withContext(Dispatchers.IO) {
            try {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, options)
                var sampleSize = 1
                val maxDim = 2048
                while (options.outWidth / sampleSize > maxDim || options.outHeight / sampleSize > maxDim) {
                    sampleSize *= 2
                }
                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val decoded = BitmapFactory.decodeFile(file.path, decodeOptions)
                if (decoded != null) {
                    bitmap = decoded.asImageBitmap()
                } else {
                    errorMsg = "Unable to decode image file"
                }
            } catch (e: Exception) {
                errorMsg = e.localizedMessage ?: "Failed to load image"
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.8f, 5f)
                    offset += pan
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = file.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
        } else if (isLoading) {
            CircularProgressIndicator(color = Color.White)
        } else if (errorMsg != null) {
            Text(
                text = errorMsg!!,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun BuiltInVideoPlayer(file: FileItem) {
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    DisposableEffect(file.path) {
        onDispose {
            try {
                videoViewRef?.stopPlayback()
                videoViewRef = null
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    val mediaController = MediaController(ctx)
                    mediaController.setAnchorView(this)
                    setMediaController(mediaController)
                    setVideoPath(file.path)
                    setOnPreparedListener { mp ->
                        mp.isLooping = false
                        start()
                    }
                    setOnErrorListener { _, _, _ -> true }
                    videoViewRef = this
                }
            },
            update = { view ->
                videoViewRef = view
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun BuiltInAudioPlayer(file: FileItem) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(1) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(file.path) {
        val mp = MediaPlayer().apply {
            try {
                setDataSource(file.path)
                prepare()
                duration = this.duration.coerceAtLeast(1)
                setOnCompletionListener {
                    isPlaying = false
                    currentPosition = duration
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        mediaPlayer = mp

        onDispose {
            try {
                mp.stop()
                mp.release()
            } catch (_: Exception) {}
            mediaPlayer = null
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying && mediaPlayer != null) {
            currentPosition = mediaPlayer?.currentPosition ?: 0
            delay(500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(100.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = file.name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(12.dp))

        Slider(
            value = currentPosition.toFloat(),
            onValueChange = { newValue ->
                currentPosition = newValue.toInt()
                mediaPlayer?.seekTo(currentPosition)
            },
            valueRange = 0f..duration.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = formatDurationMs(currentPosition.toLong()), style = MaterialTheme.typography.labelSmall)
            Text(text = formatDurationMs(duration.toLong()), style = MaterialTheme.typography.labelSmall)
        }

        Spacer(modifier = Modifier.height(16.dp))

        IconButton(
            onClick = {
                val mp = mediaPlayer ?: return@IconButton
                if (isPlaying) {
                    mp.pause()
                    isPlaying = false
                } else {
                    mp.start()
                    isPlaying = true
                }
            },
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

fun formatDurationMs(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}

@Composable
fun BuiltInPdfViewer(file: FileItem) {
    var currentPage by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }
    var pageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(file.path, currentPage) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val pfd = ParcelFileDescriptor.open(File(file.path), ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                pageCount = renderer.pageCount
                if (pageCount > 0 && currentPage in 0 until pageCount) {
                    val page = renderer.openPage(currentPage)
                    val width = page.width * 2
                    val height = page.height * 2
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    pageBitmap = bmp.asImageBitmap()
                }
                renderer.close()
                pfd.close()
            } catch (e: Exception) {
                errorMsg = e.localizedMessage ?: "Failed to render PDF page"
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            if (pageBitmap != null) {
                Image(
                    bitmap = pageBitmap!!,
                    contentDescription = "PDF Page ${currentPage + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isLoading) {
                CircularProgressIndicator(color = Color.White)
            } else if (errorMsg != null) {
                Text(text = errorMsg!!, color = Color.White)
            }
        }

        if (pageCount > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (currentPage > 0) currentPage-- },
                    enabled = currentPage > 0
                ) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous Page")
                }
                Text(
                    text = "Page ${currentPage + 1} of $pageCount",
                    style = MaterialTheme.typography.bodyMedium
                )
                IconButton(
                    onClick = { if (currentPage < pageCount - 1) currentPage++ },
                    enabled = currentPage < pageCount - 1
                ) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "Next Page")
                }
            }
        }
    }
}

@Composable
fun BuiltInTextViewer(file: FileItem) {
    var textContent by remember { mutableStateOf<String?>(null) }
    var isLoadingText by remember { mutableStateOf(true) }

    LaunchedEffect(file.path) {
        isLoadingText = true
        withContext(Dispatchers.IO) {
            try {
                val f = File(file.path)
                if (f.exists() && f.isFile && f.length() < 1_000_000) {
                    textContent = f.readText()
                } else {
                    textContent = "(File size is large. Tap 'Open in other apps' to view with full editor)"
                }
            } catch (e: Exception) {
                textContent = "Unable to read file content: ${e.message}"
            } finally {
                isLoadingText = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        if (isLoadingText) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (textContent != null) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = textContent!!,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun GenericFilePreviewCard(file: FileItem) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.InsertDriveFile,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = file.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap 'Open in other apps' below to open with compatible installed apps",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
