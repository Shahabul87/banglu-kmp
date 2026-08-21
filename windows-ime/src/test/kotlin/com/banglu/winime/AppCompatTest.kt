package com.banglu.winime

import kotlin.io.path.createTempDirectory
import kotlin.test.*

class AppCompatTest {
    // createTempDir() is deprecated; kotlin.io.path.createTempDirectory keeps
    // test output free of deprecation warnings.
    private fun tempDir() = createTempDirectory(prefix = "ac").toFile()
    private fun fresh(dir: java.io.File = tempDir()) = AppCompat(dir)

    @Test fun builtInDefaultsArePassthroughOutOfTheBox() {
        val ac = fresh()
        assertTrue(ac.isPassthrough("keepass.exe"))
        assertTrue(ac.isPassthrough("keepassxc.exe"))
        assertTrue(ac.isPassthrough("1password.exe"))
        assertTrue(ac.isPassthrough("bitwarden.exe"))
    }

    @Test fun builtInMatchIsCaseInsensitive() {
        val ac = fresh()
        assertTrue(ac.isPassthrough("KeePass.exe"))
        assertTrue(ac.isPassthrough("KEEPASSXC.EXE"))
        assertTrue(ac.isPassthrough("1Password.exe"))
    }

    @Test fun unknownExeIsNotPassthrough() {
        val ac = fresh()
        assertFalse(ac.isPassthrough("chrome.exe"))
        assertFalse(ac.isPassthrough("notepad.exe"))
    }

    @Test fun addedExeBecomesPassthrough() {
        val ac = fresh()
        ac.add("chrome.exe")
        assertTrue(ac.isPassthrough("chrome.exe"))
        assertTrue(ac.isPassthrough("CHROME.EXE"))
    }

    @Test fun addedExePersistsAcrossNewInstance() {
        val dir = tempDir()
        fresh(dir).add("MyBank.exe")
        val reloaded = fresh(dir)
        assertTrue(reloaded.isPassthrough("mybank.exe"))
    }

    @Test fun removeOfAddedExeStopsPassthrough() {
        val ac = fresh()
        ac.add("chrome.exe")
        ac.remove("chrome.exe")
        assertFalse(ac.isPassthrough("chrome.exe"))
    }

    @Test fun removeOfBuiltInPersistsAsOverrideAcrossNewInstance() {
        val dir = tempDir()
        fresh(dir).remove("keepass.exe")
        val reloaded = fresh(dir)
        assertFalse(reloaded.isPassthrough("keepass.exe"))
        // other built-ins remain untouched
        assertTrue(reloaded.isPassthrough("bitwarden.exe"))
    }

    @Test fun reAddingRemovedBuiltInRestoresPassthroughAndPersists() {
        val dir = tempDir()
        val ac = fresh(dir)
        ac.remove("keepass.exe")
        ac.add("keepass.exe")
        assertTrue(ac.isPassthrough("keepass.exe"))
        val reloaded = fresh(dir)
        assertTrue(reloaded.isPassthrough("keepass.exe"))
    }

    @Test fun corruptFileDegradesToBuiltInDefaultsOnlyNoCrash() {
        val dir = tempDir()
        java.io.File(dir, "winime-appcompat.json").writeText("{not json")
        val ac = fresh(dir)
        assertTrue(ac.isPassthrough("keepass.exe"))
        assertTrue(ac.isPassthrough("bitwarden.exe"))
        assertFalse(ac.isPassthrough("chrome.exe"))
    }

    @Test fun entriesReflectsBuiltInsMinusRemovalsPlusAdditions() {
        val ac = fresh()
        ac.remove("keepass.exe")
        ac.add("chrome.exe")
        val entries = ac.entries.map { it.lowercase() }
        assertFalse(entries.contains("keepass.exe"))
        assertTrue(entries.contains("keepassxc.exe"))
        assertTrue(entries.contains("1password.exe"))
        assertTrue(entries.contains("bitwarden.exe"))
        assertTrue(entries.contains("chrome.exe"))
    }

    @Test fun addingDuplicateDoesNotCreateDuplicateEntry() {
        val ac = fresh()
        ac.add("chrome.exe")
        ac.add("chrome.exe")
        val entries = ac.entries.map { it.lowercase() }
        assertEquals(1, entries.count { it == "chrome.exe" })
    }
}
