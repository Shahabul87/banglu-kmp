import Cocoa
import InputMethodKit
import BangluCore

protocol CandidateUI: AnyObject {
    /// `numbered`: digit hints (forming candidates, digits pick); false for
    /// the S141 prediction bar, which is click-only.
    func show(candidates: [String], highlight: Int, numbered: Bool, client: IMKTextInput)
    func hide()
    var isVisible: Bool { get }
    /// S141: a row was clicked (index into the shown list).
    var onPick: ((Int) -> Void)? { get set }
}

extension CandidateUI {
    func show(candidates: [String], highlight: Int, client: IMKTextInput) {
        show(candidates: candidates, highlight: highlight, numbered: true, client: client)
    }
}

/// Editor-style dark candidate card, caret-anchored via the client's
/// attributes(forCharacterIndex:) rect. Never takes key focus.
final class PanelCandidateUI: CandidateUI {
    private let panel: NSPanel
    private let stack = NSStackView()
    private(set) var isVisible = false
    var onPick: ((Int) -> Void)?

    init() {
        panel = NSPanel(contentRect: .zero,
                        styleMask: [.nonactivatingPanel, .borderless],
                        backing: .buffered, defer: true)
        panel.level = .popUpMenu
        panel.isOpaque = false
        panel.backgroundColor = .clear
        panel.hasShadow = true

        let container = NSVisualEffectView()
        container.material = .hudWindow
        container.state = .active
        container.wantsLayer = true
        container.layer?.cornerRadius = 8

        stack.orientation = .vertical
        stack.alignment = .leading
        stack.spacing = 2
        stack.edgeInsets = NSEdgeInsets(top: 6, left: 8, bottom: 6, right: 8)
        stack.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: container.topAnchor),
            stack.bottomAnchor.constraint(equalTo: container.bottomAnchor),
            stack.leadingAnchor.constraint(equalTo: container.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: container.trailingAnchor),
        ])
        panel.contentView = container
    }

    func show(candidates: [String], highlight: Int, numbered: Bool, client: IMKTextInput) {
        stack.arrangedSubviews.forEach { $0.removeFromSuperview() }
        let bengaliDigits = ["১", "২", "৩", "৪", "৫", "৬"]
        if !numbered {
            let header = NSTextField(labelWithString: "পরবর্তী")
            header.font = NSFont.systemFont(ofSize: 11)
            header.textColor = .secondaryLabelColor
            stack.addArrangedSubview(header)
        }
        for (i, cand) in candidates.prefix(6).enumerated() {
            let row = NSTextField(labelWithString: numbered ? "\(bengaliDigits[i])  \(cand)" : cand)
            row.font = NSFont.systemFont(ofSize: 15)
            row.textColor = (i == highlight) ? .selectedMenuItemTextColor : .labelColor
            row.drawsBackground = i == highlight
            row.backgroundColor = (i == highlight) ? .selectedContentBackgroundColor : .clear
            row.tag = i
            // The panel never activates, so a click reaches us without the
            // host losing focus — the prediction bar's only pick gesture.
            row.addGestureRecognizer(NSClickGestureRecognizer(target: self, action: #selector(rowClicked(_:))))
            stack.addArrangedSubview(row)
        }
        panel.setContentSize(stack.fittingSize)

        // Caret rect from the host; falls back to the mouse location's screen.
        var rect = NSRect.zero
        client.attributes(forCharacterIndex: 0, lineHeightRectangle: &rect)
        let origin = NSPoint(x: rect.origin.x, y: rect.origin.y - panel.frame.height - 4)
        panel.setFrameOrigin(origin)
        panel.orderFrontRegardless()
        isVisible = true
    }

    func hide() {
        panel.orderOut(nil)
        isVisible = false
    }

    @objc private func rowClicked(_ g: NSClickGestureRecognizer) {
        guard let row = g.view as? NSTextField else { return }
        onPick?(row.tag)
    }
}

/// Kept as the documented swap seam (spec §5). Not shipped in v1: the system
/// panel offers no highlight control or custom last-row rendering.
let useIMKCandidates = false
