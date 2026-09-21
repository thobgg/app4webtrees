package de.bgghome.webtrees.nativ.ui.tree

import de.bgghome.webtrees.nativ.api.DescendantNode
import de.bgghome.webtrees.nativ.api.Pedigree
import de.bgghome.webtrees.nativ.api.Person

/**
 * Ein Geschwister einer Person im Baum, mit seinen Partnern (rechts daneben) und Kindern. Die Kinder werden nur fuer
 * die Geschwister der Eltern gezeichnet - das sind die Cousins der Mittelperson.
 */
data class Sibling(val person: Person, val spouses: List<Person> = emptyList(), val children: List<Person> = emptyList())

/** Ein "+"-Kaestchen: legt relation (father|mother|spouse|child) zu relativeTo an. */
data class Placeholder(val relation: String, val relativeTo: Person, val families: List<Pair<String, String?>> = emptyList())

/** Ein Kaestchen im Baum - Person oder Platzhalter. Koordinaten in dp, linke obere Ecke. */
data class TreeBox(
    val person: Person? = null,
    val placeholder: Placeholder? = null,
    val x: Float,
    val y: Float,
    val isFocus: Boolean = false,
    /** Kekule-Nummer (nur Ahnen): 1 = Mittelperson, 2 = Vater, 3 = Mutter ... */
    val ahnen: Int? = null,
    /** Diese Person hat Eltern, die noch nicht geladen sind -> "weiter nach oben"-Symbol ueber der Karte. */
    val canExpand: Boolean = false,
) {
    val centerX get() = x + TreeLayout.BOX_W / 2
    val centerY get() = y + TreeLayout.BOX_H / 2
    val bottom get() = y + TreeLayout.BOX_H

    fun contains(px: Float, py: Float) = px >= x && px <= x + TreeLayout.BOX_W && py >= y && py <= y + TreeLayout.BOX_H
}

/** Ein Linienzug (Eckpunkte in dp). Gezeichnet wird er mit runden Ecken. */
data class Connector(val points: List<Pair<Float, Float>>)

/**
 * Sanduhr-Baum: Mittelperson, Ahnen nach oben (binaer, Kekule-Nummern), Partner daneben,
 * Nachkommen nach unten. Dazu - wie in einer Familienansicht ueblich - die Geschwister der Mittelperson und
 * ihrer Ahnen samt Partnern, vaeterlicherseits links, muetterlicherseits rechts der jeweiligen Person.
 * Reine Rechnung ohne Android/Compose - siehe TreeLayoutTest.
 */
class TreeLayout private constructor(
    val boxes: List<TreeBox>,
    val connectors: List<Connector>,
    val width: Float,
    val height: Float,
    val canEdit: Boolean,
) {

    val focus: TreeBox get() = boxes.first { it.isFocus }

    fun boxAt(x: Float, y: Float): TreeBox? = boxes.lastOrNull { it.contains(x, y) }

    /** Das Aufklapp-Symbol sitzt mittig ueber der Karte. */
    fun expandAt(x: Float, y: Float): TreeBox? = boxes.lastOrNull { box ->
        box.canExpand && (x - box.centerX) * (x - box.centerX) + (y - (box.y - EXPAND_OFFSET)) * (y - (box.y - EXPAND_OFFSET)) <= PLUS_HIT_RADIUS * PLUS_HIT_RADIUS
    }

    /** Hat diese Karte eine "+"-Lasche (Verwandte hinzufuegen)? */
    fun hasPlus(box: TreeBox): Boolean = canEdit && box.person != null && !box.person.isPrivate

    /** Die "+"-Lasche haengt mittig an der Unterkante der Karte; sie wird vor der Karte selbst geprueft. */
    fun plusAt(x: Float, y: Float): TreeBox? = boxes.lastOrNull { box ->
        hasPlus(box) && (x - box.centerX) * (x - box.centerX) + (y - box.bottom) * (y - box.bottom) <= PLUS_HIT_RADIUS * PLUS_HIT_RADIUS
    }

    companion object {
        // Kompakte Querkarten wie in gaengigen Stammbaum-Apps auf Android: rundes Foto links, Name, Jahre.
        const val BOX_W = 172f
        const val BOX_H = 60f
        const val H_GAP = 18f
        const val SPOUSE_GAP = 22f
        const val V_GAP = 64f
        const val PADDING = 56f
        const val PLUS_RADIUS = 12f
        const val PLUS_HIT_RADIUS = 20f
        /** Abstand des Aufklapp-Symbols (Mitte) ueber der Kartenoberkante */
        const val EXPAND_OFFSET = 20f
        /** Luft zwischen Geschwistergruppe und Person - groesser als zum Partner, sonst liest man den Schwager als Partner. */
        const val SIBLING_GAP = 40f

        /** Fehlende Eltern werden nur bis zu dieser Generation als "+" angeboten, sonst wird die oberste Reihe zu voll. */
        private const val PLACEHOLDER_MAX_GEN = 3

        /**
         * @param siblings Geschwister je Person (XREF -> Liste); Personen ohne Eintrag bekommen keine
         * @param cousins  Kinder der Eltern-Geschwister (Cousins der Mittelperson) in deren Reihe zeichnen
         */
        fun build(
            pedigree: Pedigree,
            descendants: DescendantNode,
            canEdit: Boolean,
            siblings: Map<String, List<Sibling>> = emptyMap(),
            cousins: Boolean = false,
        ): TreeLayout {
            val builder = Builder(pedigree, canEdit, siblings, cousins)

            builder.placeDescendants(descendants, 0f, 0, isFocus = true)
            builder.placeAncestors()

            return builder.finish()
        }
    }

    private class Builder(pedigree: Pedigree, val canEdit: Boolean, val siblings: Map<String, List<Sibling>>, val cousins: Boolean) {
        val boxes = mutableListOf<TreeBox>()
        val connectors = mutableListOf<Connector>()
        val ancestors = pedigree.ancestors.associate { it.n to it.person }
        val hasParents = pedigree.ancestors.filter { it.hasParents }.map { it.n }.toSet()
        val generations = pedigree.generations
        lateinit var focusBox: TreeBox

        // ── Nachkommen (nach unten) ──────────────────────────────────

        private fun spouseCount(node: DescendantNode, isFocus: Boolean) =
            node.families.count { it.spouse != null } + if (isFocus && canEdit) 1 else 0

        private fun unitWidth(node: DescendantNode, isFocus: Boolean) = BOX_W + spouseCount(node, isFocus) * (SPOUSE_GAP + BOX_W)

        private fun childSlots(node: DescendantNode, isFocus: Boolean): Int =
            node.families.sumOf { it.children.size } + if (isFocus && canEdit) 1 else 0

        private fun subtreeWidth(node: DescendantNode, isFocus: Boolean): Float {
            var children = node.families.sumOf { family -> family.children.sumOf { subtreeWidth(it, false).toDouble() } }.toFloat()
            if (isFocus && canEdit) children += BOX_W
            val slots = childSlots(node, isFocus)
            if (slots > 1) children += (slots - 1) * H_GAP

            return maxOf(unitWidth(node, isFocus), children)
        }

        fun placeDescendants(node: DescendantNode, left: Float, depth: Int, isFocus: Boolean): TreeBox {
            val span = subtreeWidth(node, isFocus)
            val y = depth * (BOX_H + V_GAP)
            var x = left + (span - unitWidth(node, isFocus)) / 2

            val personBox = TreeBox(person = node.person, x = x, y = y, isFocus = isFocus).also { boxes += it }
            if (isFocus) focusBox = personBox
            x += BOX_W

            // Partner rechts daneben; von jeder Verbindung gehen die gemeinsamen Kinder ab.
            val anchors = mutableListOf<Pair<Float, Float>>()
            node.families.forEach { family ->
                if (family.spouse != null) {
                    val spouseBox = TreeBox(person = family.spouse, x = x + SPOUSE_GAP, y = y).also { boxes += it }
                    connectors += Connector(listOf(personBox.x + BOX_W to personBox.centerY, spouseBox.x to spouseBox.centerY))
                    anchors += (spouseBox.x - SPOUSE_GAP / 2) to spouseBox.centerY
                    x += SPOUSE_GAP + BOX_W
                } else {
                    anchors += personBox.centerX to personBox.bottom
                }
            }

            if (isFocus && canEdit) {
                val spouseAdd = TreeBox(placeholder = Placeholder("spouse", node.person), x = x + SPOUSE_GAP, y = y).also { boxes += it }
                connectors += Connector(listOf(personBox.x + BOX_W to personBox.centerY, spouseAdd.x to spouseAdd.centerY))
            }

            // Kinderreihe, mittig unter der Einheit
            var childrenWidth = node.families.sumOf { family -> family.children.sumOf { subtreeWidth(it, false).toDouble() } }.toFloat()
            val slots = childSlots(node, isFocus)
            if (isFocus && canEdit) childrenWidth += BOX_W
            if (slots > 1) childrenWidth += (slots - 1) * H_GAP

            var childLeft = left + (span - childrenWidth) / 2
            val railY = y + BOX_H + V_GAP / 2

            node.families.forEachIndexed { index, family ->
                val (anchorX, anchorY) = anchors[index]
                val childBoxes = family.children.map { child ->
                    placeDescendants(child, childLeft, depth + 1, false).also { childLeft += subtreeWidth(child, false) + H_GAP }
                }
                connectDown(anchorX, anchorY, railY, childBoxes)
            }

            if (isFocus && canEdit) {
                val families = node.families.map { it.xref to it.spouse?.name }
                val childAdd = TreeBox(placeholder = Placeholder("child", node.person, families), x = childLeft, y = y + BOX_H + V_GAP).also { boxes += it }
                val (anchorX, anchorY) = anchors.lastOrNull() ?: (personBox.centerX to personBox.bottom)
                connectDown(anchorX, anchorY, railY, listOf(childAdd))
            }

            return personBox
        }

        private fun connectDown(anchorX: Float, anchorY: Float, railY: Float, children: List<TreeBox>) {
            if (children.isEmpty()) return

            // Je Kind ein eigener Linienzug: Anker -> Schiene -> Kind. Auf der Schiene liegen sie uebereinander.
            children.forEach { child ->
                connectors += Connector(listOf(anchorX to anchorY, anchorX to railY, child.centerX to railY, child.centerX to child.y))
            }
        }

        // ── Ahnen (nach oben) ────────────────────────────────────────

        private fun generationOf(n: Int) = 31 - Integer.numberOfLeadingZeros(n)

        /** Steht an Kekule-Platz n etwas - eine Person oder ein "+"? */
        private fun slot(n: Int): Boolean {
            if (ancestors.containsKey(n)) return true
            val child = ancestors[n / 2] ?: return false

            // Hat das Kind Eltern, die nur noch nicht geladen sind, gibt es nichts hinzuzufuegen.
            if (n / 2 in hasParents && 2 * (n / 2) !in ancestors && 2 * (n / 2) + 1 !in ancestors) return false

            return canEdit && !child.isPrivate && generationOf(n) <= PLACEHOLDER_MAX_GEN
        }

        private fun parentsOf(n: Int): List<Int> =
            if (ancestors.containsKey(n)) listOf(2 * n, 2 * n + 1).filter(::slot) else emptyList()

        private fun siblingsOf(n: Int): List<Sibling> = ancestors[n]?.let { siblings[it.xref] }.orEmpty()

        /** Vater (gerade Nummer) und Mittelperson bekommen ihre Geschwister links, die Mutter rechts - so bleibt das Paar beisammen. */
        private fun siblingsOnLeft(n: Int) = n == 1 || n % 2 == 0

        /** Cousins haengen nur unter den Geschwistern der Eltern (Plaetze 2 und 3) - in der Reihe der Mittelperson. */
        private fun cousinsOf(n: Int, sibling: Sibling): List<Person> = if (cousins && (n == 2 || n == 3)) sibling.children else emptyList()

        private fun cardsWidth(sibling: Sibling): Float = BOX_W * (1 + sibling.spouses.size) + SPOUSE_GAP * sibling.spouses.size

        private fun rowWidth(count: Int): Float = if (count == 0) 0f else count * BOX_W + (count - 1) * H_GAP

        /** Eine Einheit der Gruppe: das Geschwister mit Partnern, darunter ggf. seine Kinder - so breit wie das Breitere. */
        private fun unitWidth(n: Int, sibling: Sibling): Float = maxOf(cardsWidth(sibling), rowWidth(cousinsOf(n, sibling).size))

        private fun groupWidth(n: Int): Float {
            val group = siblingsOf(n)
            if (group.isEmpty()) return 0f

            return group.sumOf { unitWidth(n, it).toDouble() }.toFloat() + (group.size - 1) * H_GAP
        }

        private fun hasCousins(n: Int): Boolean = siblingsOf(n).any { cousinsOf(n, it).isNotEmpty() }

        /**
         * Wo die Eltern von Platz n stehen, relativ zur Mitte des Kindes: beide Eltern symmetrisch, so weit auseinander,
         * wie ihre Teilbaeume brauchen. Bei der Mittelperson (n = 1) ausserdem so weit, dass die Cousins unter den
         * Eltern-Geschwistern nicht mit ihren eigenen Geschwistern (links) und Partnern (rechts) zusammenstossen -
         * die stehen in derselben Reihe.
         */
        private fun parentOffsets(n: Int): List<Float> {
            val parents = parentsOf(n)

            // Reihe der Mittelperson: was links und rechts ihrer Kartenmitte schon steht (Geschwister | Partner, "+")
            val rowLeft = BOX_W / 2 + groupWidth(1).let { if (it == 0f) 0f else it + SIBLING_GAP }
            val rowRight = boxes.filter { it.y == 0f }.maxOf { it.x + BOX_W } - focusBox.centerX
            // So weit muss ein Elternteil mindestens von der Mitte weg, damit seine Gruppe (Rand bei BOX_W/2 + SIBLING_GAP
            // von seiner Mitte) neben dieser Reihe bleibt
            val fatherMin = if (n == 1 && hasCousins(2)) rowLeft + H_GAP - BOX_W / 2 - SIBLING_GAP else 0f
            val motherMin = if (n == 1 && hasCousins(3)) rowRight + H_GAP - BOX_W / 2 - SIBLING_GAP else 0f

            return when (parents.size) {
                2 -> {
                    val half = maxOf((extents(parents[0]).second + H_GAP + extents(parents[1]).first) / 2, fatherMin, motherMin)
                    listOf(-half, half)
                }
                1 -> listOf(if (parents[0] % 2 == 0) -fatherMin else motherMin)
                else -> emptyList()
            }
        }

        private val extentCache = HashMap<Int, Pair<Float, Float>>()

        /**
         * Wie weit der Teilbaum an Platz n nach links und rechts ueber die Mitte seiner Karte hinausragt.
         * Die Geschwistergruppe macht ihn einseitig - deshalb zwei Werte statt einer Breite.
         */
        private fun extents(n: Int): Pair<Float, Float> = extentCache.getOrPut(n) {
            val side = groupWidth(n).let { if (it == 0f) 0f else it + SIBLING_GAP }
            var left = BOX_W / 2 + if (siblingsOnLeft(n)) side else 0f
            var right = BOX_W / 2 + if (siblingsOnLeft(n)) 0f else side

            parentsOf(n).zip(parentOffsets(n)).forEach { (parent, offset) ->
                val (parentLeft, parentRight) = extents(parent)
                left = maxOf(left, parentLeft - offset)
                right = maxOf(right, parentRight + offset)
            }

            left to right
        }

        /** Legt Platz n (Kartenmitte bei centerX) und alles darueber an. Platz 1 selbst zeichnet placeDescendants. */
        private fun placeAncestor(n: Int, centerX: Float) {
            val y = -generationOf(n) * (BOX_H + V_GAP)
            val parents = parentsOf(n)

            // Eltern nebeneinander, das Paar mittig ueber dem Kind
            val parentCenters = parentOffsets(n).map { centerX + it }
            parents.forEachIndexed { index, parent -> placeAncestor(parent, parentCenters[index]) }

            if (n > 1) {
                val person = ancestors[n]
                boxes += if (person != null) {
                    // Eltern vorhanden, aber nicht geladen (oberste Reihe): Aufklapp-Symbol
                    TreeBox(person = person, x = centerX - BOX_W / 2, y = y, ahnen = n, canExpand = n in hasParents && 2 * n !in ancestors && 2 * n + 1 !in ancestors)
                } else {
                    TreeBox(placeholder = Placeholder(if (n % 2 == 0) "father" else "mother", ancestors.getValue(n / 2)), x = centerX - BOX_W / 2, y = y)
                }
            }

            val railY = y - V_GAP / 2
            if (parentCenters.isNotEmpty()) {
                parentCenters.forEach { connectors += Connector(listOf(centerX to y, centerX to railY, it to railY, it to y - V_GAP)) }
            }

            placeSiblings(n, centerX, y, if (parentCenters.isEmpty()) null else railY)
        }

        /**
         * Geschwister mit Partnern neben der Karte an Platz n. Sie haengen an derselben Elternlinie (railY) wie die
         * Person selbst; ohne Eltern im Baum haetten sie nicht geladen werden koennen.
         */
        private fun placeSiblings(n: Int, centerX: Float, y: Float, railY: Float?) {
            val group = siblingsOf(n)
            if (group.isEmpty()) return

            var unitLeft = if (siblingsOnLeft(n)) centerX - BOX_W / 2 - SIBLING_GAP - groupWidth(n) else centerX + BOX_W / 2 + SIBLING_GAP

            group.forEach { sibling ->
                val unit = unitWidth(n, sibling)
                var x = unitLeft + (unit - cardsWidth(sibling)) / 2
                val box = TreeBox(person = sibling.person, x = x, y = y).also { boxes += it }
                x += BOX_W

                // Kinder gehen von der ersten Verbindung ab (wie bei den Nachkommen), ohne Partner von der Karte selbst
                var anchor = box.centerX to box.bottom
                sibling.spouses.forEachIndexed { index, spouse ->
                    val spouseBox = TreeBox(person = spouse, x = x + SPOUSE_GAP, y = y).also { boxes += it }
                    connectors += Connector(listOf(box.x + BOX_W to box.centerY, spouseBox.x to spouseBox.centerY))
                    if (index == 0) anchor = (spouseBox.x - SPOUSE_GAP / 2) to spouseBox.centerY
                    x += SPOUSE_GAP + BOX_W
                }

                if (railY != null) connectors += Connector(listOf(box.centerX to y, box.centerX to railY, centerX to railY))

                val children = cousinsOf(n, sibling)
                if (children.isNotEmpty()) {
                    var childX = unitLeft + (unit - rowWidth(children.size)) / 2
                    val childBoxes = children.map { child ->
                        TreeBox(person = child, x = childX, y = y + BOX_H + V_GAP).also { boxes += it; childX += BOX_W + H_GAP }
                    }
                    connectDown(anchor.first, anchor.second, y + BOX_H + V_GAP / 2, childBoxes)
                }

                unitLeft += unit + H_GAP
            }
        }

        fun placeAncestors() {
            // Der Ahnenbaum haengt an der Mittelperson, die placeDescendants schon gesetzt hat.
            placeAncestor(1, focusBox.centerX)
        }

        fun finish(): TreeLayout {
            val minX = boxes.minOf { it.x } - PADDING
            val minY = boxes.minOf { it.y } - PADDING
            val maxX = boxes.maxOf { it.x + BOX_W } + PADDING
            val maxY = boxes.maxOf { it.y + BOX_H } + PADDING

            return TreeLayout(
                boxes.map { it.copy(x = it.x - minX, y = it.y - minY) },
                connectors.map { line -> Connector(line.points.map { (x, y) -> x - minX to y - minY }) },
                maxX - minX, maxY - minY, canEdit,
            )
        }
    }
}
