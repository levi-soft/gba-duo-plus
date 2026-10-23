package com.khumam.gbaduo.library

/** Translate a launcher's filesystem path to an ExternalStorage DocumentsProvider ID. */
object ExternalRomPath {
    fun documentId(path: String): String? {
        if (!path.startsWith('/') || '\u0000' in path) return null
        val parts = mutableListOf<String>()
        for (part in path.split('/')) when (part) {
            "", "." -> Unit
            ".." -> { if (parts.isEmpty()) return null; parts.removeAt(parts.lastIndex) }
            else -> parts.add(part)
        }
        val normalized = "/" + parts.joinToString("/")
        for (prefix in listOf("/storage/emulated/0/", "/storage/self/primary/", "/sdcard/")) {
            if (normalized.startsWith(prefix)) return "primary:" + normalized.removePrefix(prefix)
        }
        for (prefix in listOf("/storage/", "/mnt/media_rw/")) if (normalized.startsWith(prefix)) {
            val rest = normalized.removePrefix(prefix)
            val volume = rest.substringBefore('/')
            if (volume.matches(Regex("[0-9a-fA-F]{4}-[0-9a-fA-F]{4}")) && '/' in rest) {
                return volume + ":" + rest.substringAfter('/')
            }
        }
        return null
    }

    fun withinTree(document: String, tree: String): Boolean {
        if (!document.substringBefore(':').equals(tree.substringBefore(':'), ignoreCase = true)) return false
        val root = tree.substringAfter(':', "").trimEnd('/')
        val target = document.substringAfter(':', "")
        return root.isEmpty() || target == root || target.startsWith("$root/")
    }
}
