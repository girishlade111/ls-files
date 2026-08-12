package com.example.data.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DocumentMediaTools {

    // --- PDF UTILITIES ---

    suspend fun mergePdfFiles(inputFiles: List<File>, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val pdfDocument = PdfDocument()
            var pageIndex = 0

            for (file in inputFiles) {
                if (!file.exists() || !file.name.lowercase().endsWith(".pdf")) continue

                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)

                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val pageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, pageIndex + 1).create()
                    val pdfPage = pdfDocument.startPage(pageInfo)
                    pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDocument.finishPage(pdfPage)

                    bitmap.recycle()
                    page.close()
                    pageIndex++
                }
                renderer.close()
                pfd.close()
            }

            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun splitPdfFile(inputFile: File, outputDir: File): List<File> = withContext(Dispatchers.IO) {
        val createdFiles = mutableListOf<File>()
        try {
            val pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val baseName = inputFile.nameWithoutExtension

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdfDocument.finishPage(pdfPage)

                val outFile = File(outputDir, "${baseName}_page_${i + 1}.pdf")
                FileOutputStream(outFile).use { fos ->
                    pdfDocument.writeTo(fos)
                }
                pdfDocument.close()
                bitmap.recycle()
                page.close()
                createdFiles.add(outFile)
            }
            renderer.close()
            pfd.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        createdFiles
    }

    // --- IMAGE COMPRESSOR & CONVERTER ---

    enum class ImageFormat { JPEG, PNG, WEBP }

    suspend fun compressAndConvertImage(
        inputFile: File,
        outputFile: File,
        targetFormat: ImageFormat,
        quality: Int = 80,
        maxWidth: Int = 0,
        maxHeight: Int = 0
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val options = BitmapFactory.Options()
            if (maxWidth > 0 && maxHeight > 0) {
                options.inJustDecodeBounds = true
                BitmapFactory.decodeFile(inputFile.absolutePath, options)
                var sampleSize = 1
                while (options.outWidth / sampleSize > maxWidth || options.outHeight / sampleSize > maxHeight) {
                    sampleSize *= 2
                }
                options.inSampleSize = sampleSize
                options.inJustDecodeBounds = false
            }

            val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath, options) ?: return@withContext false

            val compressFormat = when (targetFormat) {
                ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
                ImageFormat.PNG -> Bitmap.CompressFormat.PNG
                ImageFormat.WEBP -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            }

            FileOutputStream(outputFile).use { fos ->
                bitmap.compress(compressFormat, quality.coerceIn(10, 100), fos)
            }
            bitmap.recycle()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // --- PROTECTED ARCHIVE UTILITIES ---

    suspend fun createZipArchive(
        files: List<File>,
        outputZipFile: File,
        password: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            FileOutputStream(outputZipFile).use { fos ->
                ZipOutputStream(BufferedOutputStream(fos)).use { zos ->
                    for (file in files) {
                        if (!file.exists()) continue
                        zipFileOrDirectory(file, file.name, zos)
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun zipFileOrDirectory(file: File, entryName: String, zos: ZipOutputStream) {
        if (file.isDirectory) {
            val children = file.listFiles() ?: return
            for (child in children) {
                zipFileOrDirectory(child, "$entryName/${child.name}", zos)
            }
        } else {
            val buffer = ByteArray(8192)
            FileInputStream(file).use { fis ->
                val entry = ZipEntry(entryName)
                zos.putNextEntry(entry)
                var len: Int
                while (fis.read(buffer).also { len = it } > 0) {
                    zos.write(buffer, 0, len)
                }
                zos.closeEntry()
            }
        }
    }
}
