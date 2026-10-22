package com.khumam.gbaduo.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException

/** Dev launcher entry point. Reuses existing SAF grants; never asks for broad storage access. */
object LauncherRomResolver {
    data class Target(val uri: Uri, val fileName: String)

    fun request(intent: Intent): String? = LaunchIntentResolver.resolve(intent)?.let { it.path ?: it.uriString }

    fun resolve(context: Context, request: String): Target {
        val raw = request.trim().removeSurrounding("\"")
        val uri = Uri.parse(raw)
        val target = when (uri.scheme?.lowercase()) {
            "content" -> uri
            null, "file" -> {
                val path = if (uri.scheme == null) raw else uri.path.orEmpty()
                val document = LaunchIntentResolver.documentIdForPath(path)
                val grant = context.contentResolver.persistedUriPermissions.firstOrNull { permission ->
                    permission.isReadPermission && permission.uri.authority == "com.android.externalstorage.documents" &&
                        document != null && runCatching {
                            ExternalRomPath.withinTree(document, DocumentsContract.getTreeDocumentId(permission.uri))
                        }.getOrDefault(false)
                }
                when {
                    grant != null -> DocumentsContract.buildDocumentUriUsingTree(grant.uri, document!!)
                    path.startsWith('/') && File(path).canRead() -> Uri.fromFile(File(path))
                    else -> throw IOException("Choose this ROM's folder in GBA Duo Dev once, then launch again.")
                }
            }
            else -> throw IOException("Unsupported ROM URI. Use a content URI or an absolute file path.")
        }
        val fileName = if (target.scheme == "content") {
            context.contentResolver.query(target, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } else target.lastPathSegment
        val name = fileName ?: throw IOException("Could not read the ROM filename.")
        if (!RomArchive.isLibraryFileName(name)) throw IOException("Supported ROM files: .gba, .gb, .gbc and .zip.")
        context.contentResolver.openInputStream(target)?.use {
            if (it.read() < 0) throw IOException("The ROM file is empty.")
        } ?: throw IOException("Could not open the ROM. Grant access to its folder in GBA Duo Dev.")
        return Target(target, name)
    }
}
