package com.example.data.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object SafeFolderEncryptor {

    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val KEY_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 10000
    private const val KEY_LENGTH = 256
    private const val SALT = "LSFilesSafeFolderSalt2026"

    private fun deriveKey(pin: String): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(KEY_ALGORITHM)
        val spec = PBEKeySpec(pin.toCharArray(), SALT.toByteArray(), ITERATIONS, KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    suspend fun encryptFile(inputFile: File, outputFile: File, pin: String): Boolean = withContext(Dispatchers.IO) {
        if (!inputFile.exists()) return@withContext false
        try {
            val key = deriveKey(pin)
            val cipher = Cipher.getInstance(ALGORITHM)
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec)

            FileOutputStream(outputFile).use { fos ->
                // Write IV first
                fos.write(iv)
                FileInputStream(inputFile).use { fis ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        val output = cipher.update(buffer, 0, bytesRead)
                        if (output != null) fos.write(output)
                    }
                    val outputFinal = cipher.doFinal()
                    if (outputFinal != null) fos.write(outputFinal)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            if (outputFile.exists()) {
                outputFile.delete()
            }
            false
        }
    }

    suspend fun decryptFile(inputFile: File, outputFile: File, pin: String): Boolean = withContext(Dispatchers.IO) {
        if (!inputFile.exists() || inputFile.length() < 16) return@withContext false
        try {
            val key = deriveKey(pin)
            FileInputStream(inputFile).use { fis ->
                val iv = ByteArray(16)
                val ivRead = fis.read(iv)
                if (ivRead != 16) return@withContext false

                val cipher = Cipher.getInstance(ALGORITHM)
                val ivSpec = IvParameterSpec(iv)
                cipher.init(Cipher.DECRYPT_MODE, key, ivSpec)

                FileOutputStream(outputFile).use { fos ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        val output = cipher.update(buffer, 0, bytesRead)
                        if (output != null) fos.write(output)
                    }
                    val outputFinal = cipher.doFinal()
                    if (outputFinal != null) fos.write(outputFinal)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            if (outputFile.exists()) {
                outputFile.delete()
            }
            false
        }
    }
}
