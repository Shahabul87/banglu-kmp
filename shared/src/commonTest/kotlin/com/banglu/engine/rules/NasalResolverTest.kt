package com.banglu.engine.rules

import kotlin.test.Test
import kotlin.test.assertEquals

class NasalResolverTest {

    @Test
    fun testBeforeG() {
        assertEquals('\u0999', NasalResolver.resolve("g")) // ঙ
    }

    @Test
    fun testBeforeVowel() {
        assertEquals('\u0999', NasalResolver.resolve("a")) // ঙ
    }

    @Test
    fun testBeforeConsonant() {
        assertEquals('\u0982', NasalResolver.resolve("k")) // ং
    }

    @Test
    fun testEndOfWord() {
        assertEquals('\u0982', NasalResolver.resolve(null)) // ং
    }

    @Test
    fun testBeforeE() {
        assertEquals('\u0999', NasalResolver.resolve("e")) // ঙ before vowel 'e'
    }

    @Test
    fun testBeforeI() {
        assertEquals('\u0999', NasalResolver.resolve("i")) // ঙ before vowel 'i'
    }

    @Test
    fun testBeforeO() {
        assertEquals('\u0999', NasalResolver.resolve("o")) // ঙ before vowel 'o'
    }

    @Test
    fun testBeforeU() {
        assertEquals('\u0999', NasalResolver.resolve("u")) // ঙ before vowel 'u'
    }

    @Test
    fun testResolveInContextBeforeG() {
        val result = NasalResolver.resolveInContext("bango", 2) // "ng" at position 2, next is 'o'
        assertEquals('\u0999', result.bengali) // ঙ before vowel
        assertEquals(0.90, result.confidence)
    }

    @Test
    fun testResolveInContextEndOfWord() {
        val result = NasalResolver.resolveInContext("bang", 2) // "ng" at position 2, nothing after
        assertEquals('\u0982', result.bengali) // ং at end
        assertEquals(0.95, result.confidence)
    }
}
