package com.example.ui.screens

import android.os.Environment
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.util.DocumentMediaTools
import com.example.data.util.DocumentMediaTools.ImageFormat
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Media & Document Power Tools", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
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
                    text = { Text("PDF Tools") },
                    icon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Image Resizer") },
                    icon = { Icon(Icons.Default.Image, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("ZIP Archive") },
                    icon = { Icon(Icons.Default.FolderZip, contentDescription = null) }
                )
            }

            when (selectedTab) {
                0 -> PdfToolsSection()
                1 -> ImageToolsSection()
                2 -> ArchiveToolsSection()
            }
        }
    }
}

@Composable
fun PdfToolsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pdfFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var statusText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    // Load available PDF files in Downloads / Documents
    LaunchedEffect(Unit) {
        val list = mutableListOf<File>()
        fun scan(dir: File) {
            val children = dir.listFiles() ?: return
            for (f in children) {
                if (f.isDirectory && !f.name.startsWith(".")) {
                    scan(f)
                } else if (f.name.lowercase().endsWith(".pdf")) {
                    list.add(f)
                }
            }
        }
        val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (docs.exists()) scan(docs)
        if (downloads.exists()) scan(downloads)
        pdfFiles = list.sortedBy { it.name }
    }

    var selectedPdfs by remember { mutableStateOf<Set<String>>(emptySet()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Merge or Split PDF Documents", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Select multiple PDFs below to merge them into one file, or split pages.", fontSize = 13.sp)

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (selectedPdfs.size < 2) {
                                Toast.makeText(context, "Select at least 2 PDFs to merge", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isProcessing = true
                            scope.launch {
                                val inputs = pdfFiles.filter { selectedPdfs.contains(it.absolutePath) }
                                val outDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                val outFile = File(outDir, "Merged_Document_${System.currentTimeMillis()}.pdf")
                                val success = DocumentMediaTools.mergePdfFiles(inputs, outFile)
                                isProcessing = false
                                if (success) {
                                    statusText = "Successfully merged into Downloads/${outFile.name}"
                                    Toast.makeText(context, statusText, Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Merge failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = selectedPdfs.size >= 2 && !isProcessing
                    ) {
                        Icon(Icons.Default.MergeType, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Merge Selected")
                    }

                    OutlinedButton(
                        onClick = {
                            if (selectedPdfs.size != 1) {
                                Toast.makeText(context, "Select exactly 1 PDF to split", Toast.LENGTH_SHORT).show()
                                return@OutlinedButton
                            }
                            isProcessing = true
                            scope.launch {
                                val target = pdfFiles.find { selectedPdfs.contains(it.absolutePath) }!!
                                val outDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                val created = DocumentMediaTools.splitPdfFile(target, outDir)
                                isProcessing = false
                                if (created.isNotEmpty()) {
                                    statusText = "Split into ${created.size} pages in Downloads!"
                                    Toast.makeText(context, statusText, Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Split failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = selectedPdfs.size == 1 && !isProcessing
                    ) {
                        Icon(Icons.Default.CallSplit, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Split Pages")
                    }
                }
            }
        }

        if (statusText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(statusText, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Select PDFs from Documents & Downloads:", fontWeight = FontWeight.Bold)

        if (isProcessing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(pdfFiles) { pdf ->
                val isSelected = selectedPdfs.contains(pdf.absolutePath)
                ListItem(
                    headlineContent = { Text(pdf.name, maxLines = 1) },
                    supportingContent = { Text(pdf.absolutePath, fontSize = 11.sp, maxLines = 1) },
                    leadingContent = {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = {
                                selectedPdfs = if (isSelected) selectedPdfs - pdf.absolutePath else selectedPdfs + pdf.absolutePath
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun ImageToolsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var imageFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedImage by remember { mutableStateOf<File?>(null) }
    var selectedFormat by remember { mutableStateOf(ImageFormat.JPEG) }
    var quality by remember { mutableFloatStateOf(80f) }
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val list = mutableListOf<File>()
        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)

        fun scan(dir: File) {
            val children = dir.listFiles() ?: return
            for (f in children) {
                if (f.isDirectory && !f.name.startsWith(".")) {
                    scan(f)
                } else if (listOf("jpg", "jpeg", "png", "webp").contains(f.extension.lowercase())) {
                    list.add(f)
                }
            }
        }
        if (pictures.exists()) scan(pictures)
        if (dcim.exists()) scan(dcim)
        imageFiles = list.take(50).sortedByDescending { it.lastModified() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Image Converter & Quality Compressor", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Select an image, target format, and quality ratio.", fontSize = 13.sp)

                Spacer(modifier = Modifier.height(12.dp))

                Text("Selected: ${selectedImage?.name ?: "None"}", fontWeight = FontWeight.Medium)

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Format: ", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    ImageFormat.values().forEach { format ->
                        FilterChip(
                            selected = selectedFormat == format,
                            onClick = { selectedFormat = format },
                            label = { Text(format.name) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Quality: ${quality.toInt()}%")
                Slider(
                    value = quality,
                    onValueChange = { quality = it },
                    valueRange = 10f..100f
                )

                Button(
                    onClick = {
                        val img = selectedImage ?: return@Button
                        isProcessing = true
                        scope.launch {
                            val outDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                            val ext = selectedFormat.name.lowercase()
                            val outFile = File(outDir, "Compressed_${img.nameWithoutExtension}.$ext")
                            val success = DocumentMediaTools.compressAndConvertImage(img, outFile, selectedFormat, quality.toInt())
                            isProcessing = false
                            if (success) {
                                Toast.makeText(context, "Saved to Pictures/${outFile.name}", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Compression failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = selectedImage != null && !isProcessing
                ) {
                    Icon(Icons.Default.Compress, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Compress & Save Image")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Select Image to Compress:", fontWeight = FontWeight.Bold)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(imageFiles) { img ->
                val isSelected = selectedImage?.absolutePath == img.absolutePath
                ListItem(
                    headlineContent = { Text(img.name, maxLines = 1) },
                    supportingContent = { Text(img.absolutePath, fontSize = 11.sp, maxLines = 1) },
                    trailingContent = {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedImage = img }
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun ArchiveToolsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Archive Manager", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Select files or folders in Browse screen to create compressed ZIP archives with custom file protection.", fontSize = 13.sp)
            }
        }
    }
}
