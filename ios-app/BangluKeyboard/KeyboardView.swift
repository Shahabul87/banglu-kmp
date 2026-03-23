import UIKit

protocol KeyboardViewDelegate: AnyObject {
    func keyboardView(_ view: KeyboardView, didTapKey key: String)
}

/// Custom keyboard layout view.
/// Renders a QWERTY layout optimized for Bengali phonetic typing.
class KeyboardView: UIView {

    weak var delegate: KeyboardViewDelegate?

    // QWERTY rows optimized for Bengali phonetic input
    private let rows: [[String]] = [
        ["q", "w", "e", "r", "t", "y", "u", "i", "o", "p"],
        ["a", "s", "d", "f", "g", "h", "j", "k", "l"],
        ["⇧", "z", "x", "c", "v", "b", "n", "m", "⌫"],
        ["🌐", " ", "⏎"],
    ]

    private var isShifted = false
    private var keyButtons: [[UIButton]] = []

    override init(frame: CGRect) {
        super.init(frame: frame)
        setupKeys()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupKeys()
    }

    private func setupKeys() {
        let mainStack = UIStackView()
        mainStack.axis = .vertical
        mainStack.distribution = .fillEqually
        mainStack.spacing = 6
        mainStack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(mainStack)

        NSLayoutConstraint.activate([
            mainStack.topAnchor.constraint(equalTo: topAnchor, constant: 4),
            mainStack.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 3),
            mainStack.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -3),
            mainStack.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -4),
        ])

        for row in rows {
            let rowStack = UIStackView()
            rowStack.axis = .horizontal
            rowStack.distribution = .fillEqually
            rowStack.spacing = 4

            var rowButtons: [UIButton] = []

            for key in row {
                let button = createKeyButton(key)
                rowStack.addArrangedSubview(button)
                rowButtons.append(button)
            }

            // Make space bar wider
            if row.contains(" ") {
                for (idx, key) in row.enumerated() {
                    if key == " " {
                        let spaceBtn = rowButtons[idx]
                        let constraint = spaceBtn.widthAnchor.constraint(
                            equalTo: rowButtons[0].widthAnchor,
                            multiplier: 5.0
                        )
                        constraint.priority = .defaultHigh
                        constraint.isActive = true
                    }
                }
            }

            keyButtons.append(rowButtons)
            mainStack.addArrangedSubview(rowStack)
        }
    }

    private func createKeyButton(_ key: String) -> UIButton {
        let button = UIButton(type: .system)
        button.setTitle(key, for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 22, weight: .regular)
        button.backgroundColor = keyBackgroundColor(for: key)
        button.setTitleColor(.label, for: .normal)
        button.layer.cornerRadius = 5
        button.layer.shadowColor = UIColor.black.cgColor
        button.layer.shadowOffset = CGSize(width: 0, height: 1)
        button.layer.shadowOpacity = 0.2
        button.layer.shadowRadius = 0.5

        button.addTarget(self, action: #selector(keyTapped(_:)), for: .touchUpInside)

        // Accessibility
        switch key {
        case "⌫": button.accessibilityLabel = "Delete"
        case "⏎": button.accessibilityLabel = "Return"
        case " ": button.accessibilityLabel = "Space"
        case "🌐": button.accessibilityLabel = "Switch keyboard"
        case "⇧": button.accessibilityLabel = "Shift"
        default: button.accessibilityLabel = key
        }

        return button
    }

    private func keyBackgroundColor(for key: String) -> UIColor {
        switch key {
        case "⌫", "⇧", "🌐", "⏎":
            return UIColor.systemGray3
        case " ":
            return UIColor.systemBackground
        default:
            return UIColor.systemBackground
        }
    }

    @objc private func keyTapped(_ sender: UIButton) {
        guard let key = sender.titleLabel?.text else { return }

        if key == "⇧" {
            isShifted.toggle()
            updateKeyLabels()
            return
        }

        let outputKey: String
        if isShifted && key.count == 1 && key.first?.isLetter == true {
            outputKey = key.uppercased()
            isShifted = false
            updateKeyLabels()
        } else {
            outputKey = key
        }

        delegate?.keyboardView(self, didTapKey: outputKey)

        // Brief haptic feedback
        let impact = UIImpactFeedbackGenerator(style: .light)
        impact.impactOccurred()
    }

    private func updateKeyLabels() {
        for (rowIdx, row) in rows.enumerated() {
            for (colIdx, key) in row.enumerated() {
                guard key.count == 1, key.first?.isLetter == true else { continue }
                guard rowIdx < keyButtons.count, colIdx < keyButtons[rowIdx].count else { continue }
                let button = keyButtons[rowIdx][colIdx]
                button.setTitle(isShifted ? key.uppercased() : key.lowercased(), for: .normal)
            }
        }
    }
}
