import Foundation

public enum ComposerAction: Equatable {
    case setMarked(String)
    case commit(String)
    case passThrough
    case updateCandidates([String])
    /// S141: next-word predictions to show after a commit (empty = hide).
    /// Click-only — digits keep typing digits, Return keeps its meaning.
    case updatePredictions([String])
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

    /// S141: predictions offered after the last commit, and the two-word
    /// ledger they were computed from (reset whenever the phrase breaks).
    public private(set) var predictions: [String] = []
    private var prev1 = ""
    private var prev2 = ""
    public var predicting: Bool { !forming && !predictions.isEmpty }
    private static let predictionLimit = 5

    /// Task 5 wires learning: called on every candidate pick.
    public var onPick: ((_ raw: String, _ bangla: String, _ wasPrimary: Bool) -> Void)?
    /// S141: a prediction chip was committed after `prev`.
    public var onNextWord: ((_ prev: String, _ next: String) -> Void)?

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
            var out = releasePendingSpace() + dropPredictions()
            formingRaw.append(c)
            refresh(&out)
            return out

        case .space:
            if forming {
                let word = formingBangla
                let out = commitForming()
                pendingSpace = true
                return out + offerPredictions(after: word)
            }
            let dropped = dropPredictions()
            if pendingSpace {
                pendingSpace = false
                if dariJustCommitted { dariJustCommitted = false; return dropped + [.commit(" ")] }
                dariJustCommitted = true
                resetLedger()
                return dropped + [.commit("। ")]
            }
            dariJustCommitted = false
            return dropped + [.commit(" ")]

        case .backspace:
            if forming {
                formingRaw.removeLast()
                var out: [ComposerAction] = []
                refresh(&out)
                return out
            }
            let dropped = dropPredictions()
            resetLedger()   // the words before the caret are no longer known
            if pendingSpace {
                // The user saw themselves type a space; make it real, then
                // let the host's own backspace delete it.
                pendingSpace = false
                return dropped + [.commit(" "), .passThrough]
            }
            return dropped + [.passThrough]

        case .digit(let d):
            if forming, !candidates.isEmpty, let n = d.wholeNumberValue,
               (1...6).contains(n), n - 1 < candidates.count {
                return pick(n - 1)
            }
            var out = forming ? commitForming() : releasePendingSpace() + dropPredictions()
            out.append(.commit(banglaDigits ? bengaliDigit(d) : String(d)))
            return out

        case .escape:
            guard forming else { return dropPredictions() + [.passThrough] }
            let raw = formingRaw
            clearForming()
            return [.setMarked(""), .commit(raw), .updateCandidates([])]

        case .returnKey, .tab:
            var out = forming ? commitForming() : dropPredictions()
            pendingSpace = false
            resetLedger()
            out.append(.passThrough)
            return out

        case .punctuation(let p):
            var out: [ComposerAction] = forming ? commitForming() : dropPredictions()
            resetLedger()
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
        resetLedger()
        let dropped = dropPredictions()
        guard forming else { return dropped }
        return dropped + commitForming()
    }

    public func pick(_ index: Int) -> [ComposerAction] {
        guard forming, index >= 0, index < candidates.count else { return [] }
        let choice = candidates[index]
        let wasPrimary = (choice == formingBangla)
        onPick?(formingRaw, choice, wasPrimary)
        formingBangla = choice
        let out = commitForming()
        pendingSpace = true
        return out + offerPredictions(after: choice)
    }

    /// S141: commits `predictions[index]` after the held space, holds a new
    /// space, and re-predicts (chaining) — the Windows IME contract.
    public func pickPrediction(_ index: Int) -> [ComposerAction] {
        guard predicting, index >= 0, index < predictions.count else { return [] }
        let word = predictions[index]
        var out = releasePendingSpace()
        out.append(.commit(word))
        pendingSpace = true
        dariJustCommitted = false
        if !prev1.isEmpty { onNextWord?(prev1, word) }
        return out + offerPredictions(after: word)
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

    /// Shifts the ledger past `word` and asks the engine what tends to follow.
    /// Always emits — an empty list hides a panel a previous offer opened.
    private func offerPredictions(after word: String) -> [ComposerAction] {
        prev2 = prev1
        prev1 = word
        predictions = word.isEmpty ? [] : engine.predictNext(prev2: prev2, prev1: word, limit: Composer.predictionLimit)
        return [.updatePredictions(predictions)]
    }

    private func dropPredictions() -> [ComposerAction] {
        guard !predictions.isEmpty else { return [] }
        predictions = []
        return [.updatePredictions([])]
    }

    private func resetLedger() {
        prev1 = ""
        prev2 = ""
    }
}

func bengaliDigit(_ c: Character) -> String {
    guard let n = c.wholeNumberValue, (0...9).contains(n) else { return String(c) }
    return String(Character(UnicodeScalar(0x09E6 + n)!))
}
