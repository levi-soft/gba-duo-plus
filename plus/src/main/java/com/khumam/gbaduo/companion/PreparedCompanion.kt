package com.khumam.gbaduo.companion

import android.content.Context
import com.khumam.gbaduo.companion.style.Stylesheet
import java.io.File

/** Immutable parsed sources cross the worker/UI boundary; the live tree stays on the UI thread. */
internal data class PreparedCompanion(
    val root: Widget,
    val builtIn: List<Stylesheet>,
    val styles: List<Stylesheet>,
) {
    companion object {
        fun load(context: Context, manifest: PackManifest, packDir: File): PreparedCompanion {
            val layout = LayoutParser.parseWithWarnings(File(packDir, manifest.layoutFile).readText())
            CompanionController.logWarnings(manifest.layoutFile, layout.warnings)
            return PreparedCompanion(layout.root, PackStyles.builtIn(context), PackStyles.forPack(manifest, packDir))
        }
    }
}
