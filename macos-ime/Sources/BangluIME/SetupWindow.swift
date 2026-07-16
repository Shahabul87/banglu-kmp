import Cocoa

/// S53: the assistant shown when Banglu.app is opened like a normal app
/// (double-click). One window, one big switch — no System Settings, no
/// logout. The IMK server path never shows this.
final class SetupWindowController: NSWindowController {
    private let statusDot = NSTextField(labelWithString: "●")
    private let statusLabel = NSTextField(labelWithString: "")
    private let toggleButton = NSButton(title: "", target: nil, action: nil)
    private let menuBarCheck = NSButton(checkboxWithTitle: "মেনু বারে ইনপুট মেনু দেখান",
                                        target: nil, action: nil)
    private let sameLangCheck = NSButton(checkboxWithTitle: "সব অ্যাপে একই ভাষা (প্রতি-অ্যাপ মনে রাখা বন্ধ)",
                                         target: nil, action: nil)

    convenience init() {
        let window = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 460, height: 360),
            styleMask: [.titled, .closable, .miniaturizable],
            backing: .buffered, defer: false)
        window.title = "বাংলু ইনপুট মেথড"
        window.center()
        self.init(window: window)
        buildContent()
        refresh()
    }

    private func buildContent() {
        guard let content = window?.contentView else { return }

        let title = NSTextField(labelWithString: "বাংলু — যেকোনো অ্যাপে বাংলা লিখুন")
        title.font = .systemFont(ofSize: 20, weight: .bold)

        statusDot.font = .systemFont(ofSize: 14)
        statusLabel.font = .systemFont(ofSize: 14, weight: .medium)
        let statusRow = NSStackView(views: [statusDot, statusLabel])
        statusRow.spacing = 6

        toggleButton.bezelStyle = .rounded
        toggleButton.controlSize = .large
        toggleButton.keyEquivalent = "\r"
        toggleButton.target = self
        toggleButton.action = #selector(toggle)

        menuBarCheck.target = self
        menuBarCheck.action = #selector(toggleMenuBar)
        sameLangCheck.target = self
        sameLangCheck.action = #selector(toggleSameLang)

        let steps = NSTextField(wrappingLabelWithString: """
        ৩ ধাপে শুরু করুন
        ১. উপরের বোতামে ক্লিক করুন — বাংলু চালু হয়ে যাবে
        ২. 🌐 Globe (বা Ctrl+Space) চেপে বাংলু বেছে নিন
        ৩. লিখুন: kemon acho → কেমন আছো
        """)
        steps.font = .systemFont(ofSize: 13)

        let hint = NSTextField(wrappingLabelWithString:
            "বন্ধ করতে এখানেই এক ক্লিক — System Settings থেকে remove করবেন না, দরকার নেই।")
        hint.font = .systemFont(ofSize: 11)
        hint.textColor = .secondaryLabelColor

        let stack = NSStackView(views: [title, statusRow, toggleButton,
                                        menuBarCheck, sameLangCheck, steps, hint])
        stack.orientation = .vertical
        stack.alignment = .leading
        stack.spacing = 14
        stack.edgeInsets = NSEdgeInsets(top: 24, left: 28, bottom: 24, right: 28)
        stack.translatesAutoresizingMaskIntoConstraints = false
        content.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: content.topAnchor),
            stack.leadingAnchor.constraint(equalTo: content.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: content.trailingAnchor),
        ])
    }

    func refresh() {
        let enabled = InputSourceControl.isEnabled
        statusDot.textColor = enabled ? .systemGreen : .systemGray
        statusLabel.stringValue = enabled ? "বাংলু চালু আছে" : "বাংলু বন্ধ আছে"
        toggleButton.title = enabled ? "বন্ধ করুন" : "এক ক্লিকে চালু করুন"
        menuBarCheck.state = InputSourceControl.showsInputMenu ? .on : .off
        sameLangCheck.state = InputSourceControl.sameLanguageEverywhere ? .on : .off
    }

    @objc private func toggle() {
        if InputSourceControl.isEnabled {
            InputSourceControl.disable()
        } else if !InputSourceControl.enable() {
            let alert = NSAlert()
            alert.messageText = "চালু করা গেল না"
            alert.informativeText = "একবার লগ আউট করে আবার লগ ইন করুন, তারপর আবার চেষ্টা করুন। (macOS-এর ইনপুট তালিকা মাঝে মাঝে নতুন করে পড়তে হয়।)"
            alert.runModal()
        }
        refresh()
    }

    @objc private func toggleMenuBar() {
        InputSourceControl.showsInputMenu = (menuBarCheck.state == .on)
    }

    @objc private func toggleSameLang() {
        InputSourceControl.sameLanguageEverywhere = (sameLangCheck.state == .on)
    }
}
