package com.khumam.gbaduo.companion

import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

/**
 * What a pack *does*, as a SHA-256: every file outside `assets/` (its scripts, manifest,
 * layout and styles), plus declared behavior files even under assets/, by path and content. The player's "let this pack change the game"
 * answer is kept with it, so a different or updated pack under the same id is asked
 * about again. Images and fonts (`assets/`) can't change what a tap does, so a new
 * picture doesn't ask again. Declared scripts/layout/styles do, wherever they live.
 */
object PackFingerprint {

    fun of(packDir: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val root = packDir.canonicalFile
        val manifest = JSONObject(File(root, "manifest.json").readText())
        val behaviorFiles = buildSet {
            for (key in listOf("script", "gameScript", "layout")) {
                manifest.optString(key).takeIf { it.isNotBlank() }?.let { add(it) }
            }
            manifest.optJSONArray("styles")?.let { styles ->
                for (index in 0 until styles.length()) add(styles.getString(index))
            }
        }.map { name ->
            val file = File(root, name).canonicalFile
            require(file.path.startsWith(root.path + File.separator)) { "Behavior file outside pack" }
            file.relativeTo(root).invariantSeparatorsPath
        }.toSet()
        val assetBehavior = behaviorFiles.filter { it.startsWith("assets/") }
        val files = root.walkTopDown()
            .onEnter { directory ->
                val relative = directory.relativeTo(root).invariantSeparatorsPath
                (relative != "assets" && !relative.startsWith("assets/")) ||
                    assetBehavior.any { it.startsWith("$relative/") }
            }
            .filter { it.isFile }
            .map { it.relativeTo(root).invariantSeparatorsPath to it }
            .filter { (relative, _) -> !relative.startsWith("assets/") || relative in behaviorFiles }
            .sortedBy { it.first }
        for ((path, file) in files) {
            digest.update(path.toByteArray(Charsets.UTF_8))
            digest.update(0)
            digest.update(file.length().toString().toByteArray(Charsets.US_ASCII))
            digest.update(0)
            file.inputStream().use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
