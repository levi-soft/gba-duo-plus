package com.khumam.gbaduo.companion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as DrawRect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.khumam.gbaduo.companion.Widget
import com.khumam.gbaduo.companion.layout.*
import com.khumam.gbaduo.companion.style.*
import com.khumam.gbaduo.companion.tree.CompanionNode
import kotlin.math.roundToInt

/** Uniform, non-interactive image/box grids can share one draw surface instead of thousands of layouts. */
internal fun rasterGridEligible(node: CompanionNode, container: ContainerStyle): Boolean {
    if (node.widget !is Widget.ItemList || container.display != Display.GRID || container.rowGap != 0f || container.columnGap != 0f) return false
    if (container.columns.isEmpty() || container.rows.isEmpty() || (container.columns + container.rows).any { it !is GridTracks.Track.Fr || it.fr != 1f }) return false
    val rootStyle = node.computed ?: return false
    if (Properties.SIDES.any { rootStyle.padding(it) != 0f || rootStyle.borderWidth(it) != 0f }) return false
    fun supported(n: CompanionNode): Boolean {
        if (n.kind == CompanionNode.Kind.EMPTY) return true
        val s = n.computed ?: return false
        if (n.onClick != null || s.transitions.isNotEmpty() || s.keyword("transform") != "none" || s.keyword("position") != "static" || s.keyword("overflow") != "visible") return false
        if (n.widget !is Widget.Box && n.widget !is Widget.Column && n.widget !is Widget.Row && n.widget !is Widget.Image && n.widget !is Widget.Spacer) return false
        if (s.zIndex != null && s.zIndex != 0 || s.number("order") != 0f) return false
        if (n.isImage && s.keyword("object-fit") != "fill") return false
        return n.children.all { supported(it) }
    }
    return node.children.isNotEmpty() && node.children.all { supported(it) }
}

@Composable
internal fun CssRasterGrid(node: CompanionNode, platform: CompanionPlatform, container: ContainerStyle, modifier: Modifier) {
    val density = LocalDensity.current.density
    val painter = remember(platform, density) { RasterGridPainter(platform, density) }
    Canvas(modifier) {
        val cells = node.children.filter { it.kind != CompanionNode.Kind.EMPTY && !it.hidden }
        val w = size.width / container.columns.size
        val h = size.height / container.rows.size
        for ((i, cell) in cells.withIndex()) {
            val x = (i % container.columns.size) * w
            val y = (i / container.columns.size) * h
            inset(x, y, size.width - x - w, size.height - y - h) { painter.run { paintNode(cell) } }
        }
    }
}

private class RasterGridPainter(val platform: CompanionPlatform, val density: Float) {
    private val paints = HashMap<ComputedStyle, BoxPaint>()
    private val boxes = HashMap<ComputedStyle, BoxStyle>()
    private data class Geometry(val container: ContainerStyle, val children: List<BoxStyle>, val w: Float, val h: Float)
    private val geometry = object : LinkedHashMap<Geometry, LayoutResult>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Geometry, LayoutResult>?) = size > 256
    }
    fun DrawScope.paintNode(n: CompanionNode) {
        if (n.hidden) return
        val s = n.computed ?: return
        if (s.keyword("display") == "none" || s.keyword("visibility") in listOf("hidden", "collapse") || s.opacity == 0f) return
        val alpha = s.opacity
        if (alpha < 1f) drawContext.canvas.saveLayer(DrawRect(Offset.Zero, size), Paint().apply { this.alpha = alpha })
        try {
            if (paints.size > 512) paints.clear()
            drawCssBox(paints.getOrPut(s) { boxPaint(n, s, density, platform) }, platform)
            if (n.isImage) {
                n.image?.let { drawImage(it, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = if (s.keyword("image-rendering") in listOf("pixelated", "crisp-edges")) FilterQuality.None else FilterQuality.Low) }
                return
            }
            val children = n.children.filter { !it.hidden && it.computed?.keyword("display") != "none" }
            if (children.isEmpty()) return
            if (boxes.size > 512) boxes.clear()
            fun edge(side: String) = (s.padding(side) + s.borderWidth(side)) * density
            val left = edge("left"); val top = edge("top"); val right = edge("right"); val bottom = edge("bottom")
            val w = (size.width - left - right).coerceAtLeast(0f)
            val h = (size.height - top - bottom).coerceAtLeast(0f)
            val container = ContainerStyle.from(s).scaled(density)
            val styles = children.map { boxes.getOrPut(it.computed!!) { BoxStyle.from(it.computed!!).scaled(density) } }
            val key = Geometry(container, styles, w, h)
            val result = geometry.getOrPut(key) {
                LayoutEngine.layout(container, Constraint.Exactly(w), Constraint.Exactly(h), styles.map { box -> object : LayoutChild {
                    override val style = box
                    override fun measure(width: Constraint, height: Constraint) = Size(width.definite ?: 0f, height.definite ?: 0f)
                } })
            }
            for ((i, child) in children.withIndex()) {
                val r = result.rects[i] ?: continue
                val x = left + r.x; val y = top + r.y
                inset(x, y, size.width - x - r.width, size.height - y - r.height) { paintNode(child) }
            }
        } finally { if (alpha < 1f) drawContext.canvas.restore() }
    }
}
