package com.omersusin.pitube.utils

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Resolves a persisted SAF tree URI to a real filesystem path when the tree
 * lives on the primary volume ("primary:Movies/piTube" →
 * "/storage/emulated/0/Movies/piTube"). Returns null for volumes that cannot
 * be mapped (SD cards / cloud providers), where callers fall back to the
 * default download directory.
 */
object SafTreeResolver {

    fun resolve(context: Context, treeUri: String): String? {
        if (treeUri.isBlank()) return null
        val uri = Uri.parse(treeUri)
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            // Plain filesystem path stored directly.
            return treeUri.takeIf { it.startsWith("/") }
        }
        return runCatching {
            val docId = DocumentsContract.getTreeDocumentId(uri) ?: return null
            if (!docId.startsWith("primary:")) return null
            val relative = docId.removePrefix("primary:")
            val base = android.os.Environment.getExternalStorageDirectory().absolutePath
            val path = if (relative.isEmpty()) base else "$base/$relative"
            java.io.File(path).canonicalPath
        }.getOrNull()
    }
}
