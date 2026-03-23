import UIKit

/// Suggestion bar displayed above the keyboard.
/// Shows Bengali word suggestions as the user types phonetically.
class SuggestionBar: UIView {

    var onSuggestionSelected: ((String) -> Void)?

    private let scrollView = UIScrollView()
    private let stackView = UIStackView()
    private let phoneticsLabel = UILabel()

    override init(frame: CGRect) {
        super.init(frame: frame)
        setupUI()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupUI()
    }

    private func setupUI() {
        backgroundColor = .secondarySystemBackground

        // Phonetic buffer display (left side)
        phoneticsLabel.font = .systemFont(ofSize: 14, weight: .medium)
        phoneticsLabel.textColor = .secondaryLabel
        phoneticsLabel.translatesAutoresizingMaskIntoConstraints = false
        addSubview(phoneticsLabel)

        // Suggestions scroll view
        scrollView.showsHorizontalScrollIndicator = false
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        addSubview(scrollView)

        stackView.axis = .horizontal
        stackView.spacing = 8
        stackView.alignment = .center
        stackView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(stackView)

        // Separator at bottom
        let separator = UIView()
        separator.backgroundColor = .separator
        separator.translatesAutoresizingMaskIntoConstraints = false
        addSubview(separator)

        NSLayoutConstraint.activate([
            phoneticsLabel.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 12),
            phoneticsLabel.centerYAnchor.constraint(equalTo: centerYAnchor),
            phoneticsLabel.widthAnchor.constraint(lessThanOrEqualToConstant: 80),

            scrollView.leadingAnchor.constraint(equalTo: phoneticsLabel.trailingAnchor, constant: 8),
            scrollView.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -8),
            scrollView.topAnchor.constraint(equalTo: topAnchor),
            scrollView.bottomAnchor.constraint(equalTo: bottomAnchor),

            stackView.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor),
            stackView.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor),
            stackView.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor),
            stackView.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor),
            stackView.heightAnchor.constraint(equalTo: scrollView.frameLayoutGuide.heightAnchor),

            separator.leadingAnchor.constraint(equalTo: leadingAnchor),
            separator.trailingAnchor.constraint(equalTo: trailingAnchor),
            separator.bottomAnchor.constraint(equalTo: bottomAnchor),
            separator.heightAnchor.constraint(equalToConstant: 0.5),
        ])
    }

    func update(suggestions: [String], phoneticBuffer: String = "") {
        phoneticsLabel.text = phoneticBuffer

        // Clear existing suggestion buttons
        stackView.arrangedSubviews.forEach { $0.removeFromSuperview() }

        for suggestion in suggestions {
            let button = createSuggestionButton(suggestion)
            stackView.addArrangedSubview(button)
        }
    }

    private func createSuggestionButton(_ text: String) -> UIButton {
        let button = UIButton(type: .system)
        button.setTitle(text, for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 18, weight: .medium)
        button.setTitleColor(.label, for: .normal)
        button.contentEdgeInsets = UIEdgeInsets(top: 6, left: 12, bottom: 6, right: 12)
        button.backgroundColor = .systemBackground
        button.layer.cornerRadius = 6
        button.layer.borderWidth = 0.5
        button.layer.borderColor = UIColor.separator.cgColor

        button.addAction(UIAction { [weak self] _ in
            guard let text = button.titleLabel?.text else { return }
            self?.onSuggestionSelected?(text)
        }, for: .touchUpInside)

        return button
    }
}
