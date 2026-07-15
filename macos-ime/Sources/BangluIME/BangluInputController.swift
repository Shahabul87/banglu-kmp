import Cocoa
import InputMethodKit
import BangluCore

/// IMK glue: NSEvents → ComposerKey, ComposerActions → the client app.
/// All conversion logic lives in Composer/EngineJS — this file only routes.
@objc(BangluInputController)
class BangluInputController: IMKInputController {
    private var composer: Composer?
    private var candidateUI: CandidateUI = NullCandidateUI()

    override func activateServer(_ sender: Any!) {
        super.activateServer(sender)
        AppState.reloadLearned()
        let bundleID = (sender as? IMKTextInput)?.bundleIdentifier()
        let plain = AppState.compat.mode(forBundleID: bundleID) == .plain
        let engine = AppState.engine
        let c = Composer(engine: engine, plainMode: plain)
        c.onPick = { raw, bangla, wasPrimary in
            // Invariant #3: the engine's own first choice teaches nothing.
            guard !wasPrimary, AppState.store.learningEnabled else { return }
            AppState.store.recordPick(raw: raw, bangla: bangla)
            engine.recordPick(raw: raw, bangla: bangla)
        }
        composer = c
    }

    override func deactivateServer(_ sender: Any!) {
        flush(to: sender)
        candidateUI.hide()
        super.deactivateServer(sender)
    }

    override func commitComposition(_ sender: Any!) {
        flush(to: sender)
    }

    override func recognizedEvents(_ sender: Any!) -> Int {
        Int(NSEvent.EventTypeMask.keyDown.rawValue)
    }

    override func handle(_ event: NSEvent!, client sender: Any!) -> Bool {
        guard let composer, let event, event.type == .keyDown else { return false }
        // Never eat shortcuts.
        if event.modifierFlags.contains(.command) || event.modifierFlags.contains(.control) {
            if composer.forming { apply(composer.focusLost(), to: sender) }
            return false
        }
        guard let key = composerKey(for: event, composerActive: composer.forming) else {
            if composer.forming { apply(composer.focusLost(), to: sender) }
            return false
        }
        let actions = composer.handle(key)
        return apply(actions, to: sender)
    }

    override func menu() -> NSMenu! {
        let menu = NSMenu()
        menu.addItem(withTitle: "বাংলু এডিটর খুলুন",
                     action: #selector(openEditor), keyEquivalent: "").target = self
        menu.addItem(withTitle: "শিখুন — টিউটোরিয়াল",
                     action: #selector(openTutorial), keyEquivalent: "").target = self
        let learn = menu.addItem(withTitle: "শেখা চালু/বন্ধ",
                                 action: #selector(toggleLearning), keyEquivalent: "")
        learn.target = self
        learn.state = AppState.store.learningEnabled ? .on : .off
        return menu
    }

    @objc private func openEditor() {
        NSWorkspace.shared.open(URL(fileURLWithPath: "/Applications/Banglu.app"))
    }

    @objc private func openTutorial() {
        let cfg = NSWorkspace.OpenConfiguration()
        cfg.arguments = ["--tutorial"]
        NSWorkspace.shared.openApplication(
            at: URL(fileURLWithPath: "/Applications/Banglu.app"),
            configuration: cfg, completionHandler: nil)
    }

    @objc private func toggleLearning() {
        AppState.store.setLearningEnabled(!AppState.store.learningEnabled)
    }

    // MARK: - routing

    private func composerKey(for event: NSEvent, composerActive: Bool) -> ComposerKey? {
        switch event.keyCode {
        case 51: return .backspace
        case 53: return .escape
        case 36: return .returnKey
        case 48: return .tab
        case 125: return composerActive ? .arrowDown : nil
        case 126: return composerActive ? .arrowUp : nil
        default: break
        }
        guard let chars = event.characters, chars.count == 1, let ch = chars.first else {
            return nil
        }
        if ch == " " { return .space }
        if ch.isLetter, ch.isASCII { return .letter(ch) }
        if ch.isNumber, ch.isASCII { return .digit(ch) }
        if ch.isNewline { return .returnKey }
        // Any other printable = punctuation; control chars fall through.
        if !ch.isASCII || ch.asciiValue.map({ $0 >= 32 }) == true {
            return .punctuation(String(ch))
        }
        return nil
    }

    @discardableResult
    private func apply(_ actions: [ComposerAction], to sender: Any!) -> Bool {
        guard let client = sender as? IMKTextInput else { return false }
        var handled = !actions.isEmpty
        for action in actions {
            switch action {
            case .setMarked(let text):
                let attrs: [NSAttributedString.Key: Any] =
                    [.underlineStyle: NSUnderlineStyle.single.rawValue]
                client.setMarkedText(
                    NSAttributedString(string: text, attributes: attrs),
                    selectionRange: NSRange(location: text.utf16.count, length: 0),
                    replacementRange: NSRange(location: NSNotFound, length: 0))
            case .commit(let text):
                client.insertText(text,
                    replacementRange: NSRange(location: NSNotFound, length: 0))
            case .updateCandidates(let list):
                if list.isEmpty { candidateUI.hide() }
                else { candidateUI.show(candidates: list, highlight: composer?.highlight ?? 0,
                                        client: client) }
            case .passThrough:
                handled = false
            }
        }
        return handled
    }

    private func flush(to sender: Any!) {
        guard let composer else { return }
        apply(composer.focusLost(), to: sender)
        candidateUI.hide()
    }
}

/// Task 7 replaces this with the real IMKCandidates/NSPanel implementations.
protocol CandidateUI {
    func show(candidates: [String], highlight: Int, client: IMKTextInput)
    func hide()
}
final class NullCandidateUI: CandidateUI {
    func show(candidates: [String], highlight: Int, client: IMKTextInput) {}
    func hide() {}
}
