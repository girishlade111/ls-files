package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.FileItem
import com.example.service.PeerDevice
import com.example.service.QuickShareManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileShareActionSheet(
    filesToShare: List<FileItem>,
    onDismiss: () -> Unit,
    onShareSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isScanning by remember { mutableStateOf(true) }
    var discoveredPeers by remember { mutableStateOf<List<PeerDevice>>(emptyList()) }
    var activeTransferPeer by remember { mutableStateOf<PeerDevice?>(null) }
    var transferProgress by remember { mutableFloatStateOf(0f) }
    var isTransferring by remember { mutableStateOf(false) }
    var transferCompleted by remember { mutableStateOf(false) }
    var showOpenWithSheet by remember { mutableStateOf(false) }

    // Pulse animation for radar scanner
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val radarScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RadarScale"
    )

    // Simulate scanning for nearby Quick Share peers
    LaunchedEffect(Unit) {
        delay(800)
        discoveredPeers = QuickShareManager.mockNearbyPeers
        isScanning = false
    }

    // Function to start simulated P2P transfer
    fun initiateP2PTransfer(peer: PeerDevice) {
        activeTransferPeer = peer
        isTransferring = true
        transferProgress = 0f
        transferCompleted = false

        coroutineScope.launch {
            for (i in 1..10) {
                delay(200)
                transferProgress = i / 10f
            }
            delay(100)
            isTransferring = false
            transferCompleted = true
            onShareSuccess("Successfully transferred via Quick Share to ${peer.name}")
            delay(1200)
            onDismiss()
        }
    }

    val totalSize = filesToShare.sumOf { it.sizeBytes }
    val titleText = if (filesToShare.size == 1) "Share '${filesToShare.first().name}'" else "Share ${filesToShare.size} Items"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("file_share_action_sheet")
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Total Payload: ${formatFileSize(totalSize)} · Peer-to-Peer Transfer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_share_sheet")
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // PRIMARY HERO: Quick Share P2P Integration Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .testTag("quick_share_primary_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Quick Share Title & Radar Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .scale(if (isScanning) radarScale else 1.0f)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WifiTethering,
                                contentDescription = "Quick Share Radar",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Quick Share",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "PRIMARY P2P",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (isScanning) "Searching nearby Quick Share devices..." else "${discoveredPeers.size} nearby peers found",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Active Transfer UI View
                    if (isTransferring || transferCompleted) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (transferCompleted) Icons.Outlined.CheckCircle else Icons.Outlined.Sync,
                                    contentDescription = null,
                                    tint = if (transferCompleted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (transferCompleted) "Sent to ${activeTransferPeer?.name}!" else "Sending to ${activeTransferPeer?.name} via Quick Share...",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${(transferProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { transferProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        // Nearby Discovered Peers Horizontal List
                        Text(
                            text = "Tap a nearby device to send instantly:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (isScanning) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Scanning via Wi-Fi Direct & Bluetooth...",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(discoveredPeers) { peer ->
                                    Card(
                                        onClick = { initiateP2PTransfer(peer) },
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.testTag("peer_device_${peer.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = when (peer.deviceType) {
                                                    "Phone" -> Icons.Outlined.Smartphone
                                                    "Tablet" -> Icons.Outlined.Tablet
                                                    else -> Icons.Outlined.Computer
                                                },
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = peer.name,
                                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                                )
                                                Text(
                                                    text = peer.connectionType,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Icon(
                                                imageVector = Icons.Outlined.Send,
                                                contentDescription = "Send",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Launch System Quick Share Intent Button
                        Button(
                            onClick = {
                                val opened = QuickShareManager.launchQuickShareIntent(context, filesToShare)
                                if (opened) {
                                    onShareSuccess("Opened Quick Share system dialog")
                                    onDismiss()
                                } else {
                                    onShareSuccess("Could not open Quick Share dialog")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("launch_native_quick_share_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Outlined.WifiTethering, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Android Quick Share Dialog")
                        }

                        if (filesToShare.size == 1 && !filesToShare.first().isDirectory) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showOpenWithSheet = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_with_intent_selector_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Outlined.OpenInNew, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open with... (App Selector)")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECONDARY SHARE OPTIONS
            Text(
                text = "Other Sharing Options",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Copy Path / Link
                OutlinedIconButton(
                    onClick = {
                        val paths = filesToShare.joinToString("\n") { it.path }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("File Path", paths)
                        clipboard.setPrimaryClip(clip)
                        onShareSuccess("File path copied to clipboard")
                        onDismiss()
                    },
                    modifier = Modifier.testTag("copy_file_path_btn")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy Path")
                    }
                }

                // Cloud Share
                OutlinedIconButton(
                    onClick = {
                        onShareSuccess("Shared to Cloud Drive")
                        onDismiss()
                    },
                    modifier = Modifier.testTag("cloud_share_btn")
                ) {
                    Icon(Icons.Outlined.CloudUpload, contentDescription = "Cloud Share")
                }

                // Standard Android Chooser
                OutlinedIconButton(
                    onClick = {
                        QuickShareManager.launchQuickShareIntent(context, filesToShare)
                        onDismiss()
                    },
                    modifier = Modifier.testTag("system_chooser_btn")
                ) {
                    Icon(Icons.Outlined.MoreHoriz, contentDescription = "More Apps")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showOpenWithSheet && filesToShare.size == 1) {
        OpenWithBottomSheet(
            file = filesToShare.first(),
            onDismiss = { showOpenWithSheet = false },
            onOpenSuccess = { msg ->
                onShareSuccess(msg)
                onDismiss()
            }
        )
    }
}
