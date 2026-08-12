package com.example.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.FileItem
import java.io.File

data class PeerDevice(
    val id: String,
    val name: String,
    val deviceType: String, // e.g. "Phone", "Tablet", "Laptop"
    val connectionType: String, // "Wi-Fi Direct", "Bluetooth", "Quick Share P2P"
    val signalStrength: Int // 1-100
)

object QuickShareManager {

    val mockNearbyPeers = listOf(
        PeerDevice("peer_1", "Pixel 8 Pro", "Phone", "Wi-Fi Direct", 95),
        PeerDevice("peer_2", "Galaxy Tab S9", "Tablet", "Quick Share P2P", 88),
        PeerDevice("peer_3", "Office Workstation PC", "Laptop", "Wi-Fi Direct", 76),
        PeerDevice("peer_4", "Nearby Chromebook", "Laptop", "Bluetooth", 65)
    )

    fun launchQuickShareIntent(context: Context, files: List<FileItem>): Boolean {
        if (files.isEmpty()) return false

        val uris = ArrayList<Uri>()
        for (item in files) {
            val file = File(item.path)
            if (file.exists() && !file.isDirectory) {
                val uri = try {
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
                uris.add(uri)
            }
        }

        if (uris.isEmpty()) return false

        val mimeType = if (files.size == 1) files.first().mimeType.ifBlank { "*/*" } else "*/*"

        val quickShareIntent = Intent().apply {
            action = if (uris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND
            type = mimeType
            if (uris.size == 1) {
                putExtra(Intent.EXTRA_STREAM, uris.first())
            } else {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage("com.google.android.gms")
        }

        return try {
            context.startActivity(quickShareIntent)
            true
        } catch (e: Exception) {
            val chooserIntent = Intent().apply {
                action = if (uris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND
                type = mimeType
                if (uris.size == 1) {
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                } else {
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(chooserIntent, "Quick Share")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(chooser)
                true
            } catch (ex: Exception) {
                false
            }
        }
    }
}
