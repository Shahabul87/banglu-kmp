package com.banglu.engine

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S135 (F-004, production audit): saved-email identity assist has its own
 * switch. When it is off — or the personal dictionary is off — the adapter
 * neither records nor SURFACES addresses, so a remembered address can never
 * reappear for a user who opted out.
 */
class S135IdentityAssistSwitchTest {

    @AfterTest
    fun tearDown() {
        SmartEngineAdapter.reset()
    }

    @Test
    fun identitySwitchOffBlocksRecordingAndSurfacing() {
        SmartEngineAdapter.initializeSync()
        SmartEngineAdapter.configureLearning(enabled = true, personalDictionary = true, identityAssist = true)
        SmartEngineAdapter.recordIdentity("rahim@gmail.com")
        assertEquals(listOf("rahim@gmail.com"), SmartEngineAdapter.identitySavedFills())

        SmartEngineAdapter.configureLearning(enabled = true, personalDictionary = true, identityAssist = false)
        assertTrue(SmartEngineAdapter.identitySavedFills().isEmpty(), "off → nothing surfaced")
        assertTrue(SmartEngineAdapter.identityDomainSuggestions("karim@gm").isEmpty(), "off → no completions")
        SmartEngineAdapter.recordIdentity("karim@yahoo.com")

        SmartEngineAdapter.configureLearning(enabled = true, personalDictionary = true, identityAssist = true)
        assertEquals(listOf("rahim@gmail.com"), SmartEngineAdapter.identitySavedFills(), "off-window recording dropped")
    }

    @Test
    fun personalDictionaryOffAlsoSilencesIdentityAssist() {
        SmartEngineAdapter.initializeSync()
        SmartEngineAdapter.configureLearning(enabled = true, personalDictionary = true, identityAssist = true)
        SmartEngineAdapter.recordIdentity("rahim@gmail.com")

        SmartEngineAdapter.configureLearning(enabled = true, personalDictionary = false, identityAssist = true)
        assertTrue(SmartEngineAdapter.identitySavedFills().isEmpty())
        assertTrue(SmartEngineAdapter.identityDomainSuggestions("rahim@g").isEmpty())
    }

    @Test
    fun clearIdentityForgetsEverythingWhileSwitchStaysOn() {
        SmartEngineAdapter.initializeSync()
        SmartEngineAdapter.configureLearning(enabled = true, personalDictionary = true, identityAssist = true)
        SmartEngineAdapter.recordIdentity("rahim@gmail.com")
        SmartEngineAdapter.clearIdentity()
        assertTrue(SmartEngineAdapter.identitySavedFills().isEmpty())
    }
}
