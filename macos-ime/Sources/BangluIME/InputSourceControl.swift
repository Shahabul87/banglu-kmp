import Carbon
import Foundation

/// S53: one-click enable/disable for the Banglu input source — the fix for
/// the install-UX failure of 2026-07-16. The cardinal rule learned there:
/// NEVER remove the source (System Settings "remove" poisons the TIS cache
/// until re-login); we only ever register + enable/disable, all of which
/// take effect immediately in the same session.
enum InputSourceControl {
    static let inputSourceID = "com.banglu.inputmethod"

    private static func findSource() -> TISInputSource? {
        let filter = [kTISPropertyInputSourceID as String: inputSourceID] as CFDictionary
        let list = TISCreateInputSourceList(filter, true)?
            .takeRetainedValue() as? [TISInputSource]
        return list?.first
    }

    /// Registers the installed bundle with Text Input Services (idempotent).
    @discardableResult
    static func register() -> Bool {
        // Register the bundle we are actually running from — survives the
        // app being installed under a different name/location.
        let url = Bundle.main.bundleURL
        return TISRegisterInputSource(url as CFURL) == noErr
    }

    static var isDiscovered: Bool { findSource() != nil }

    static var isEnabled: Bool {
        guard let src = findSource(),
              let ptr = TISGetInputSourceProperty(src, kTISPropertyInputSourceIsEnabled)
        else { return false }
        return Unmanaged<CFBoolean>.fromOpaque(ptr).takeUnretainedValue() == kCFBooleanTrue
    }

    /// One-click on: register (harmless if already) then enable.
    @discardableResult
    static func enable() -> Bool {
        register()
        guard let src = findSource() else { return false }
        return TISEnableInputSource(src) == noErr
    }

    /// Graceful off — the source stays discoverable and instantly re-onable.
    @discardableResult
    static func disable() -> Bool {
        guard let src = findSource() else { return false }
        return TISDisableInputSource(src) == noErr
    }

    /// Also make Banglu the CURRENT input source (used by "switch now").
    @discardableResult
    static func select() -> Bool {
        guard let src = findSource() else { return false }
        return TISSelectInputSource(src) == noErr
    }

    // ── The two system prefs the assistant exposes ──

    /// System Settings → Keyboard → "Show Input menu in menu bar".
    static var showsInputMenu: Bool {
        get {
            CFPreferencesCopyAppValue("visible" as CFString,
                                      "com.apple.TextInputMenu" as CFString) as? Bool ?? false
        }
        set {
            CFPreferencesSetAppValue("visible" as CFString,
                                     newValue as CFBoolean,
                                     "com.apple.TextInputMenu" as CFString)
            CFPreferencesAppSynchronize("com.apple.TextInputMenu" as CFString)
        }
    }

    /// Inverse of "Automatically switch to a document's input source" —
    /// true = one language everywhere (the setting most people expect).
    static var sameLanguageEverywhere: Bool {
        get {
            let dict = CFPreferencesCopyAppValue(
                "AppleGlobalTextInputProperties" as CFString,
                kCFPreferencesAnyApplication) as? [String: Any]
            let perContext = dict?["TextInputGlobalPropertyPerContextInput"] as? Bool ?? false
            return !perContext
        }
        set {
            let dict = ["TextInputGlobalPropertyPerContextInput": !newValue] as CFDictionary
            CFPreferencesSetAppValue("AppleGlobalTextInputProperties" as CFString,
                                     dict, kCFPreferencesAnyApplication)
            CFPreferencesAppSynchronize(kCFPreferencesAnyApplication)
        }
    }
}
