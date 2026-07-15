import Foundation

public enum ComposerAction: Equatable {
    case setMarked(String)
    case commit(String)
    case passThrough
    case updateCandidates([String])
}

public enum ComposerKey: Equatable {
    case letter(Character)
    case space
    case backspace
    case digit(Character)
    case escape
    case returnKey, tab
    case punctuation(String)
    case arrowUp, arrowDown
}

/// The IME's typing state machine — the editor's EditorState contract
/// re-expressed for IMK (spec §4). Pure logic, no IMK imports.
///
/// দাঁড়ি uses the pending-space model: IMK cannot edit committed text, so a
/// space after a word commits the word and HOLDS the space; what arrives
/// next decides whether it becomes " ", "। ", or is swallowed (tight
/// punctuation).
public final class Composer {
    private let engine: BangluEngine
    private let banglaDigits: Bool
    private let plainMode: Bool

    public private(set) var formingRaw = ""
    public private(set) var candidates: [String] = []
    public private(set) var highlight = 0
    private var formingBangla = ""
    private var pendingSpace = false
    private var dariJustCommitted = false

    /// Task 5 wires learning: called on every candidate pick.
    public var onPick: ((_ raw: String, _ bangla: String, _ wasPrimary: Bool) -> Void)?

    private static let tightPunctuation: Set<String> = [",", "।", "?", "!"]

    public init(engine: BangluEngine, banglaDigits: Bool = true, plainMode: Bool = false) {
        self.engine = engine
        self.banglaDigits = banglaDigits
        self.plainMode = plainMode
    }

    public var forming: Bool { !formingRaw.isEmpty }

    public func handle(_ key: ComposerKey) -> [ComposerAction] {
        switch key {
        case .letter(let c):
            var out = releasePendingSpace()
            formingRaw.append(c)
            refresh(&out)
            return out

        case .space:
            if forming {
                let out = commitForming()
                pendingSpace = true
                return out
            }
            if pendingSpace {
                pendingSpace = false
                if dariJustCommitted { dariJustCommitted = false; return [.commit(" ")] }
                dariJustCommitted = true
                return [.commit("। ")]
            }
            dariJustCommitted = false
            return [.commit(" ")]

        case .backspace:
            if forming {
                formingRaw.removeLast()
                var out: [ComposerAction] = []
                refresh(&out)
                return out
            }
            if pendingSpace {
                // The user saw themselves type a space; make it real, then
                // let the host's own backspace delete it.
                pendingSpace = false
                return [.commit(" "), .passThrough]
            }
            return [.passThrough]

        case .digit(let d):
            if forming, !candidates.isEmpty, let n = d.wholeNumberValue,
               (1...6).contains(n), n - 1 < candidates.count {
                return pick(n - 1)
            }
            var out = forming ? commitForming() : releasePendingSpace()
            out.append(.commit(banglaDigits ? bengaliDigit(d) : String(d)))
            return out

        case .escape:
            guard forming else { return [.passThrough] }
            let raw = formingRaw
            clearForming()
            return [.setMarked(""), .commit(raw), .updateCandidates([])]

        case .returnKey, .tab:
            var out = forming ? commitForming() : []
            pendingSpace = false
            out.append(.passThrough)
            return out

        case .punctuation(let p):
            var out: [ComposerAction] = forming ? commitForming() : []
            let mapped = (p == ".") ? "।" : p
            if pendingSpace {
                pendingSpace = false
                if !Composer.tightPunctuation.contains(mapped) { out.append(.commit(" ")) }
            }
            out.append(.commit(mapped))
            dariJustCommitted = (mapped == "।")
            return out

        case .arrowUp, .arrowDown:
            guard forming, !candidates.isEmpty else { return [.passThrough] }
            let delta = (key == .arrowDown) ? 1 : -1
            highlight = (highlight + delta + candidates.count) % candidates.count
            return []   // panel re-renders from `highlight`
        }
    }

    public func focusLost() -> [ComposerAction] {
        pendingSpace = false
        guard forming else { return [] }
        return commitForming()
    }

    public func pick(_ index: Int) -> [ComposerAction] {
        guard forming, index >= 0, index < candidates.count else { return [] }
        let choice = candidates[index]
        let wasPrimary = (choice == formingBangla)
        onPick?(formingRaw, choice, wasPrimary)
        formingBangla = choice
        let out = commitForming()
        pendingSpace = true
        return out
    }

    // MARK: - internals

    private func refresh(_ out: inout [ComposerAction]) {
        if formingRaw.isEmpty {
            clearForming()
            out.append(.setMarked(""))
            out.append(.updateCandidates([]))
            return
        }
        formingBangla = engine.convert(formingRaw)
        var list = engine.suggestions(formingRaw, limit: 6)
        if !list.contains(formingRaw) { list.append(formingRaw) }   // raw = inline English
        candidates = list
        highlight = 0
        out.append(.setMarked(plainMode ? "" : formingBangla))
        out.append(.updateCandidates(candidates))
    }

    /// WYSIWYG: commits exactly formingBangla — never re-converts.
    private func commitForming() -> [ComposerAction] {
        let text = formingBangla
        clearForming()
        dariJustCommitted = false
        return [.setMarked(""), .commit(text), .updateCandidates([])]
    }

    private func releasePendingSpace() -> [ComposerAction] {
        guard pendingSpace else { return [] }
        pendingSpace = false
        dariJustCommitted = false
        return [.commit(" ")]
    }

    private func clearForming() {
        formingRaw = ""
        formingBangla = ""
        candidates = []
        highlight = 0
    }
}

func bengaliDigit(_ c: Character) -> String {
    guard let n = c.wholeNumberValue, (0...9).contains(n) else { return String(c) }
    return String(Character(UnicodeScalar(0x09E6 + n)!))
}
