import UIKit
// import BangluKeyboardEngine  // Uncomment when SPM dependency is linked

/// Main keyboard extension controller.
/// Manages the custom Bengali phonetic keyboard lifecycle.
class KeyboardViewController: UIInputViewController {

    // MARK: - Properties

    private var keyboardView: KeyboardView!
    private var suggestionBar: SuggestionBar!
    // private let engine = PhoneticEngineLite()  // Uncomment with engine import

    private var phoneticBuffer = ""
    private var suggestions: [String] = []

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
        setupEngine()
    }

    override func viewWillLayoutSubviews() {
        super.viewWillLayoutSubviews()
        // Adjust keyboard height
        // Standard: 216pt portrait, 162pt landscape
    }

    override func textWillChange(_ textInput: UITextInput?) {
        // Called when text is about to change in the document
    }

    override func textDidChange(_ textInput: UITextInput?) {
        // Called after text changes in the document
        updateReturnKeyType()
    }

    // MARK: - Setup

    private func setupUI() {
        // Remove default keyboard view
        guard let inputView = inputView else { return }
        inputView.allowsSelfSizing = true

        let containerStack = UIStackView()
        containerStack.axis = .vertical
        containerStack.translatesAutoresizingMaskIntoConstraints = false
        inputView.addSubview(containerStack)

        NSLayoutConstraint.activate([
            containerStack.topAnchor.constraint(equalTo: inputView.topAnchor),
            containerStack.leadingAnchor.constraint(equalTo: inputView.leadingAnchor),
            containerStack.trailingAnchor.constraint(equalTo: inputView.trailingAnchor),
            containerStack.bottomAnchor.constraint(equalTo: inputView.bottomAnchor),
        ])

        // Suggestion bar (top)
        suggestionBar = SuggestionBar()
        suggestionBar.onSuggestionSelected = { [weak self] suggestion in
            self?.selectSuggestion(suggestion)
        }
        suggestionBar.translatesAutoresizingMaskIntoConstraints = false
        suggestionBar.heightAnchor.constraint(equalToConstant: 44).isActive = true
        containerStack.addArrangedSubview(suggestionBar)

        // Keyboard keys (bottom)
        keyboardView = KeyboardView()
        keyboardView.delegate = self
        keyboardView.translatesAutoresizingMaskIntoConstraints = false
        keyboardView.heightAnchor.constraint(equalToConstant: 180).isActive = true
        containerStack.addArrangedSubview(keyboardView)
    }

    private func setupEngine() {
        // engine.initialize()
    }

    // MARK: - Input Handling

    func handleKeyTap(_ key: String) {
        switch key {
        case "⌫":  // Backspace
            handleBackspace()
        case "⏎":  // Return
            handleReturn()
        case " ":   // Space - commit current word
            commitCurrentWord()
            textDocumentProxy.insertText(" ")
        case "🌐":  // Globe - switch keyboard
            advanceToNextInputMode()
        default:
            phoneticBuffer += key
            updateSuggestions()
        }
    }

    private func handleBackspace() {
        if phoneticBuffer.isEmpty {
            textDocumentProxy.deleteBackward()
        } else {
            phoneticBuffer.removeLast()
            if phoneticBuffer.isEmpty {
                suggestions = []
                suggestionBar.update(suggestions: [])
            } else {
                updateSuggestions()
            }
        }
    }

    private func handleReturn() {
        commitCurrentWord()
        textDocumentProxy.insertText("\n")
    }

    private func commitCurrentWord() {
        guard !phoneticBuffer.isEmpty else { return }

        // Convert phonetic to Bengali
        // let result = engine.convert(phoneticBuffer)
        // textDocumentProxy.insertText(result.bengali)

        // Placeholder: insert phonetic buffer as-is
        textDocumentProxy.insertText(phoneticBuffer)

        phoneticBuffer = ""
        suggestions = []
        suggestionBar.update(suggestions: [])
    }

    private func selectSuggestion(_ suggestion: String) {
        textDocumentProxy.insertText(suggestion)
        // engine.saveLearnedWord(phonetic: phoneticBuffer, bengali: suggestion)
        phoneticBuffer = ""
        suggestions = []
        suggestionBar.update(suggestions: [])
    }

    private func updateSuggestions() {
        guard !phoneticBuffer.isEmpty else {
            suggestions = []
            suggestionBar.update(suggestions: [])
            return
        }

        // let engineSuggestions = engine.getSuggestions(phoneticBuffer, limit: 6)
        // suggestions = engineSuggestions.map { $0.bengali }
        // suggestionBar.update(suggestions: suggestions)

        // Placeholder
        suggestionBar.update(suggestions: [])
    }

    private func updateReturnKeyType() {
        // Could adapt return key appearance based on context
    }
}

// MARK: - KeyboardViewDelegate

extension KeyboardViewController: KeyboardViewDelegate {
    func keyboardView(_ view: KeyboardView, didTapKey key: String) {
        handleKeyTap(key)
    }
}
