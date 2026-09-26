package de.bgghome.webtrees.nativ

import de.bgghome.webtrees.nativ.api.IndividualDetail
import de.bgghome.webtrees.nativ.api.halfSiblings
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** Halbgeschwister aus stepFamilies (api4webtrees 1.8.0) - Form wie am Testserver gemessen (Peter Phillips, demo). */
class HalfSiblingsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun paternalFirstWithoutSelfAndFullSiblings() {
        val detail = json.decodeFromString<IndividualDetail>(
            """
            {"person":{"xref":"I1"},
             "parentFamilies":[{"xref":"F1","husband":{"xref":"D"},"wife":{"xref":"M"},"children":[{"xref":"I1"},{"xref":"I2"}]}],
             "stepFamilies":[
               {"xref":"F3","parent":"M","spouse":{"xref":"X"},"children":[{"xref":"H2","sex":"M"}]},
               {"xref":"F2","parent":"D","spouse":{"xref":"Y"},"children":[{"xref":"H1","sex":"F","hasParents":true,"childrenCount":0},{"xref":"I2"}]}
             ]}
            """.trimIndent(),
        )
        val half = detail.halfSiblings()
        assertEquals(listOf("H1" to true, "H2" to false), half.map { it.person.xref to it.paternal })
    }

    @Test
    fun olderModuleHasNone() {
        val detail = json.decodeFromString<IndividualDetail>("""{"person":{"xref":"I1"},"parentFamilies":[]}""")
        assertEquals(emptyList<Any>(), detail.halfSiblings())
    }
}
