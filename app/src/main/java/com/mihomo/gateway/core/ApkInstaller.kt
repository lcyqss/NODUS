package com.mihomo.gateway.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

object ApkInstaller {

    fun install(context: Context, file: File): Result<Unit> {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(
            file.absolutePath,
            PackageManager.GET_SIGNING_CERTIFICATES
        ) ?: return Result.failure(IllegalStateException("无法读取 APK"))
        if (archive.packageName != context.packageName) {
            return Result.failure(IllegalStateException("包名不一致"))
        }
        val current = runCatching {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }.getOrNull() ?: return Result.failure(IllegalStateException("无法读取当前版本"))
        if (archive.longVersionCode <= current.longVersionCode) {
            return Result.failure(IllegalStateException("APK 版本不高于当前版本"))
        }
        val archiveSigners = archive.signingInfo?.apkContentsSigners?.map { sha256(it.toByteArray()) }.orEmpty()
        val currentSigners = current.signingInfo?.apkContentsSigners?.map { sha256(it.toByteArray()) }.orEmpty()
        if (archiveSigners.isEmpty() || archiveSigners != currentSigners) {
            return Result.failure(IllegalStateException("APK 签名不一致"))
        }
        return runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.recoverCatching {
            // Fall back to the system package installer URI for devices without a viewer.
            val uri = Uri.fromFile(file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
