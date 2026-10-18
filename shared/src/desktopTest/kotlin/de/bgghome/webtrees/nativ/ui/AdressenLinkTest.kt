package de.bgghome.webtrees.nativ.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals

/** Adressen in Seitenangaben werden eigene Links (Issue 29); Satzzeichen am Ende bleiben Text. */
class AdressenLinkTest {
    private fun links(text: String): List<Pair<String, String>> {
        val s = buildAnnotatedString { mitAdressen(text, Color.Blue) }
        assertEquals(text, s.text)
        return s.getLinkAnnotations(0, s.length).map { (it.item as LinkAnnotation.Url).url to s.text.substring(it.start, it.end) }
    }

    @Test fun adressen() {
        assertEquals(emptyList(), links("Taufen 1833, Nr. 19"))
        assertEquals(listOf("https://example.org/a?b=1" to "https://example.org/a?b=1"), links("Seite 4, https://example.org/a?b=1."))
        assertEquals(listOf("https://www.archion.de/x" to "www.archion.de/x"), links("(siehe www.archion.de/x)"))
        assertEquals(2, links("http://a.de und https://b.de").size)
    }
}
