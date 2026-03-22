package com.banglu.engine.rules

/**
 * VowelResolver - Simple resolver for vowel ambiguity
 *
 * Provides default resolutions for ambiguous vowel sounds in Bengali.
 * Each method returns the independent form and the dependent (kar) form
 * with a confidence score.
 */
object VowelResolver {

    /**
     * Resolve "i" sound — default is short ই/ি
     * (vs long ঈ/ী)
     */
    fun resolveI(isIndependent: Boolean = true): VowelResult {
        return VowelResult(
            bengali = if (isIndependent) 'ই' else 'ি',
            confidence = 0.85
        )
    }

    /**
     * Resolve "u" sound — default is short উ/ু
     * (vs long ঊ/ূ)
     */
    fun resolveU(isIndependent: Boolean = true): VowelResult {
        return VowelResult(
            bengali = if (isIndependent) 'উ' else 'ু',
            confidence = 0.90
        )
    }

    /**
     * Resolve "e" sound — default is এ/ে
     * (vs ঐ/ৈ)
     */
    fun resolveE(isIndependent: Boolean = true): VowelResult {
        return VowelResult(
            bengali = if (isIndependent) 'এ' else 'ে',
            confidence = 0.90
        )
    }

    /**
     * Resolve "o" sound — default is ও/ো
     * (vs ঔ/ৌ)
     */
    fun resolveO(isIndependent: Boolean = true): VowelResult {
        return VowelResult(
            bengali = if (isIndependent) 'ও' else 'ো',
            confidence = 0.90
        )
    }
}

data class VowelResult(
    val bengali: Char,
    val confidence: Double
)
