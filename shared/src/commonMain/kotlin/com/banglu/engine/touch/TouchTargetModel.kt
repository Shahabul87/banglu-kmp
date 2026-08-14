package com.banglu.engine.touch

/**
 * S99: probabilistic touch targeting — the Samsung/Gboard trick where a tap
 * near a key boundary is resolved by the LANGUAGE, not just geometry: after
 * "th", a tap grazing the w/e boundary means "e" (the), because P(e|h)
 * dwarfs P(w|h).
 *
 * Deliberately conservative:
 *  - only the outer [EDGE_ZONE] of a key can ever flip; a center tap is
 *    always exactly the key the user pressed;
 *  - the neighbor must beat the tapped letter by a margin that GROWS
 *    steeply with penetration depth — at the very boundary a ~2x more
 *    likely letter wins, a few percent inside it needs to be orders of
 *    magnitude more likely, past the zone it can never win;
 *  - no word context, no vote: without a previous letter the tap is final.
 *
 * Pure and platform-free; the caller supplies the roman context (the BN
 * composing buffer or the EN word prefix) and the tapped key's horizontal
 * neighbors from the layout row.
 */
object TouchTargetModel {

    /** Fraction of the key width, each side, where a flip is possible. */
    private const val EDGE_ZONE = 0.28f

    /** Evidence ratio required AT the boundary (grows steeply inside). */
    private const val BASE_MARGIN = 2.0

    /** Extra orders of magnitude of evidence required across the zone. */
    private const val DEPTH_DECADES = 2.5

    /**
     * @param context roman letters typed so far in the current word ("" = no vote)
     * @param tapped the key geometry says was pressed
     * @param leftNeighbor / rightNeighbor same-row letter neighbors (null at row edges)
     * @param xFraction horizontal press position inside the tapped key, 0..1
     * @param english true = English table, false = romanized-Bangla table
     * @return the resolved character — [tapped] unless the language strongly disagrees
     */
    fun resolve(
        context: String,
        tapped: Char,
        leftNeighbor: Char?,
        rightNeighbor: Char?,
        xFraction: Float,
        english: Boolean,
    ): Char {
        val tappedLower = tapped.lowercaseChar()
        if (tappedLower !in 'a'..'z') return tapped
        val prev = context.lastOrNull()?.lowercaseChar() ?: return tapped
        if (prev !in 'a'..'z') return tapped

        val (candidate, penetration) = when {
            xFraction <= EDGE_ZONE && leftNeighbor != null ->
                leftNeighbor to (xFraction / EDGE_ZONE)
            xFraction >= 1f - EDGE_ZONE && rightNeighbor != null ->
                rightNeighbor to ((1f - xFraction) / EDGE_ZONE)
            else -> return tapped
        }
        val candLower = candidate.lowercaseChar()
        if (candLower !in 'a'..'z' || candLower == tappedLower) return tapped

        val table = if (english) CharBigramData.ENGLISH else CharBigramData.BANGLA_ROMAN
        val row = (prev - 'a') * 26
        val tappedCount = table[row + (tappedLower - 'a')] + 1.0
        val candCount = table[row + (candLower - 'a')] + 1.0

        // penetration 0 = at the boundary, 1 = zone's inner edge.
        val requiredRatio = BASE_MARGIN * pow10(DEPTH_DECADES * penetration)
        return if (candCount / tappedCount >= requiredRatio) {
            if (tapped.isUpperCase()) candLower.uppercaseChar() else candLower
        } else {
            tapped
        }
    }

    private fun pow10(x: Double): Double = kotlin.math.exp(x * kotlin.math.ln(10.0))
}
