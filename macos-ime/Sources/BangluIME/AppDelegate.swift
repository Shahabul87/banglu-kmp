import Cocoa
import InputMethodKit

/// S53: launch-context branching. The same executable is BOTH the silent
/// IMK server (when macOS launches it for typing) and the one-click setup
/// assistant (when a person opens it). Heuristics:
///  - launched while the source is NOT enabled → almost certainly a person
///    installing/re-enabling → show the assistant;
///  - re-opened while already running (Finder double-click) → show it;
///  - `--setup` argument → always show it (the editor/menu can deep-link).
/// The plist stays LSBackgroundOnly; the activation policy is raised at
/// runtime only when a window must appear.
final class AppDelegate: NSObject, NSApplicationDelegate {
    private var server: IMKServer?
    private var setup: SetupWindowController?
    private var statusItem: NSStatusItem?

    func applicationDidFinishLaunching(_ notification: Notification) {
        guard let connectionName =
            Bundle.main.infoDictionary?["InputMethodConnectionName"] as? String,
            let bundleID = Bundle.main.bundleIdentifier
        else { fatalError("InputMethodConnectionName missing from Info.plist") }

        server = IMKServer(name: connectionName, bundleIdentifier: bundleID)
        installStatusItem()

        if !InputSourceControl.isEnabled || CommandLine.arguments.contains("--setup") {
            showSetup()
        }
    }

    func applicationShouldHandleReopen(_ sender: NSApplication,
                                       hasVisibleWindows flag: Bool) -> Bool {
        showSetup()
        return true
    }

    // MARK: - Setup window

    func showSetup() {
        if setup == nil { setup = SetupWindowController() }
        NSApp.setActivationPolicy(.accessory)
        setup?.refresh()
        setup?.showWindow(nil)
        setup?.window?.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
    }

    // MARK: - Menu-bar control (always one click away)

    private func installStatusItem() {
        let item = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        item.button?.title = "বা"
        let menu = NSMenu()
        let toggle = NSMenuItem(title: "", action: #selector(toggleEnabled), keyEquivalent: "")
        toggle.target = self
        menu.addItem(toggle)
        menu.addItem(NSMenuItem.separator())
        let setupItem = NSMenuItem(title: "সেটআপ খুলুন…", action: #selector(openSetup), keyEquivalent: "")
        setupItem.target = self
        menu.addItem(setupItem)
        let editorItem = NSMenuItem(title: "বাংলু এডিটর খুলুন", action: #selector(openEditor), keyEquivalent: "")
        editorItem.target = self
        menu.addItem(editorItem)
        menu.delegate = self
        item.menu = menu
        statusItem = item
    }

    @objc private func toggleEnabled() {
        if InputSourceControl.isEnabled {
            InputSourceControl.disable()
        } else {
            InputSourceControl.enable()
        }
        setup?.refresh()
    }

    @objc private func openSetup() { showSetup() }

    @objc private func openEditor() {
        NSWorkspace.shared.open(URL(fileURLWithPath: "/Applications/Banglu.app"))
    }
}

extension AppDelegate: NSMenuDelegate {
    func menuNeedsUpdate(_ menu: NSMenu) {
        menu.items.first?.title =
            InputSourceControl.isEnabled ? "বাংলু বন্ধ করুন" : "বাংলু চালু করুন"
    }
}
