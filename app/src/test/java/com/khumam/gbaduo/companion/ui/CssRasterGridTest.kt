package com.khumam.gbaduo.companion.ui
import com.khumam.gbaduo.companion.LayoutParser
import com.khumam.gbaduo.companion.tree.CompanionTree
import com.khumam.gbaduo.companion.layout.ContainerStyle
import com.khumam.gbaduo.companion.style.CssParser
import org.junit.Assert.*
import org.junit.Test
class CssRasterGridTest {
    private fun eligible(template: String, extra: String = ""): Boolean {
        val tree = CompanionTree(LayoutParser.parse("""{"type":"list","items":"cells","template":$template}"""), pack=listOf(CssParser.parse("list { display:grid; grid-template-columns:repeat(2,1fr); grid-template-rows:1fr; } box { display:stack; width:100%; height:100%; } image { object-fit:fill; } $extra")))
        tree.update(mapOf("cells" to listOf(mapOf("id" to 1), mapOf("id" to 2))))
        return rasterGridEligible(tree.root, ContainerStyle.from(tree.root.computed!!))
    }
    @Test fun imageBoxGridUsesSingleSurface() {
        assertTrue(eligible("""{"type":"box","children":[{"type":"image","src":"asset:tile.png"}]}"""))
    }
    @Test fun interactiveAndTextGridsKeepNormalRenderer() {
        assertFalse(eligible("""{"type":"box","onClick":{"set":{"selected":true}},"children":[]}"""))
        assertFalse(eligible("""{"type":"text","text":"name"}"""))
    }
    @Test fun unsupportedVisualFeaturesKeepNormalRenderer() {
        val box="""{"type":"box","children":[]}"""
        assertFalse(eligible(box,"box { transform:rotate(20deg); }"))
        assertFalse(eligible(box,"box { transition:opacity 100ms; }"))
        assertFalse(eligible(box,"list { padding:4px; }"))
    }
}
