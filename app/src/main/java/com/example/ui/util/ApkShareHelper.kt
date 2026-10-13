package com.example.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.DecimalFormat

object ApkShareHelper {

    fun getApkFile(context: Context): File? {
        return try {
            val sourceDir = context.applicationInfo?.sourceDir
            if (!sourceDir.isNullOrBlank()) {
                val file = File(sourceDir)
                if (file.exists() && file.canRead()) file else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun getApkFileSizeFormatted(context: Context): String {
        val file = getApkFile(context) ?: return "N/A"
        val bytes = file.length()
        if (bytes <= 0) return "N/A"
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return DecimalFormat("#,##0.0").format(mb) + " MB"
    }

    fun getAppVersion(context: Context): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    suspend fun shareApkFile(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val sourceFile = getApkFile(context)
                ?: return@withContext Result.failure(IllegalStateException("APK file could not be accessed on this device."))

            val appName = try {
                context.getString(R.string.app_name).replace("\\s+".toRegex(), "_")
            } catch (_: Exception) {
                "Memorizer"
            }
            val versionName = getAppVersion(context)
            val fileName = "${appName}_v${versionName}.apk"

            val cacheDir = File(context.cacheDir, "shared_apk")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val targetFile = File(cacheDir, fileName)
            sourceFile.copyTo(targetFile, overwrite = true)

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                targetFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, apkUri)
                putExtra(Intent.EXTRA_SUBJECT, "$appName v$versionName APK")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Here is the $appName installation APK file (v$versionName). You can install it directly on Android."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share $appName APK")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveApkToUri(context: Context, destUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val sourceFile = getApkFile(context)
                ?: return@withContext Result.failure(IllegalStateException("APK file could not be accessed on this device."))

            context.contentResolver.openOutputStream(destUri)?.use { output ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(IllegalStateException("Unable to open output stream."))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
