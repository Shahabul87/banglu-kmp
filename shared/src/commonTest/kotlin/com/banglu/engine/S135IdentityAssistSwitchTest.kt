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
        SmartEngineAdapter.recordIdentity("rahim@example-corp.com")
        assertEquals(listOf("rahim@example-corp.com"), SmartEngineAdapter.identitySavedFills())
        assertEquals(listOf("karim@example-corp.com"), SmartEngineAdapter.identityDomainSuggestions("karim@exam"))

        SmartEngineAdapter.configureLearning(enabled = true, personalDictionary = true, identityAssist = false)
        assertTrue(SmartEngineAdapter.identitySavedFills().isEmpty(), "off → nothing surfaced")
        assertTrue(SmartEngineAdapter.identityDomainSuggestions("karim@exam").isEmpty(), "off → saved domain hidden")
        assertEquals(listOf("karim@gmail.com"), SmartEngineAdapter.identityDomainSuggestions("karim@gm", 1), "built-in list still completes")
        SmartEngineAdapter.recordIdentity("karim@yahoo.com")

        SmartEngineAdapter.configureLearning(enabled = true, personalDictionary = true, identityAssist = true)
        assertEquals(listOf("rahim@example-corp.com"), SmartEngineAdapter.identitySavedFills(), "off-window recording dropped")
    }

    @Test
    fun personalDictionaryOffAlsoSilencesIdentityAssist() {
        SmartEngineAdapter.initializeSync()
        SmartEngineAdapter.configureLearning(enabled = true, personalDictionary = true, identityAssist = true)
        SmartEngineAdapter.recordIdentity("rahim@example-corp.com")

        SmartEngineAdapter.configureLearning(enabled = true, personalDictionary = false, identityAssist = true)
        assertTrue(SmartEngineAdapter.identitySavedFills().isEmpty())
        assertTrue(SmartEngineAdapter.identityDomainSuggestions("rahim@exam").isEmpty())
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
