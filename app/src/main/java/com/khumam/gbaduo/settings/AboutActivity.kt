package com.khumam.gbaduo.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import com.khumam.gbaduo.R

/** Settings > About: version, source and a word on what GBADuo is. */
class AboutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(16))
            setBackgroundColor(getColor(R.color.background))
        }

        root.addView(title(getString(R.string.app_name), 26f, true))
        root.addView(body("Version ${version ?: "?"}\n\n" +
            "Community Plus edition, based on GBA Duo 0.1.6 by khumam and its contributors. " +
            "Includes RetroAchievements, additional shaders, saved controls and companion tools.\n\n" +
            "Play GBA, GBC and GB games with a companion screen on a dual-screen handheld.\n\n" +
            "Fan-made, not affiliated with Nintendo. Use your own legally dumped ROMs."))

        root.addView(body("With gratitude to the mGBA team, Lua.org and PUC-Rio, " +
            "RetroAchievements/rcheevos contributors, and the Libretro shader authors.\n\n" +
            "GPL-3.0 project; third-party components retain their own licenses. " +
            "Provided without warranty. No endorsement by the upstream projects is implied."))

        for ((label, url) in listOf(
            "GBA Duo Plus source and credits" to getString(R.string.github_url),
            "Original GBA Duo by khumam" to "https://github.com/khumam/gba-duo",
            "License and third-party notices" to "https://github.com/levi-soft/gba-duo-plus/blob/main/CREDITS.md",
        )) root.addView(TextView(this).apply {
            text = label
            setTextColor(getColor(R.color.accent))
            textSize = 15f
            setPadding(0, dp(12), 0, 0)
            setOnClickListener {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun title(text: String, size: Float, bold: Boolean) = TextView(this).apply {
        this.text = text
        setTextColor(getColor(R.color.text_primary))
        textSize = size
        setTypeface(typeface, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }

    private fun body(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(getColor(R.color.text_secondary))
        textSize = 15f
        setLineSpacing(dp(2).toFloat(), 1f)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
