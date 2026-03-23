import SwiftUI

/// Main editor view for the Banglu app.
/// Provides a rich text editing experience with Bengali phonetic typing.
struct ContentView: View {
    @State private var bengaliText = ""
    @State private var phoneticBuffer = ""
    @State private var suggestions: [String] = []
    @State private var showSettings = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Bengali text display area
                ScrollView {
                    Text(bengaliText.isEmpty ? "এখানে বাংলায় লিখুন..." : bengaliText)
                        .font(.system(size: 20))
                        .foregroundColor(bengaliText.isEmpty ? .gray : .primary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding()
                }
                .frame(maxHeight: .infinity)

                Divider()

                // Suggestion bar
                if !suggestions.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 12) {
                            ForEach(suggestions, id: \.self) { suggestion in
                                Button(action: {
                                    selectSuggestion(suggestion)
                                }) {
                                    Text(suggestion)
                                        .font(.system(size: 18))
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 8)
                                        .background(Color.blue.opacity(0.1))
                                        .cornerRadius(8)
                                }
                            }
                        }
                        .padding(.horizontal)
                    }
                    .frame(height: 44)
                    .background(Color(.systemBackground))
                }

                // Phonetic input field
                HStack {
                    TextField("Type in English...", text: $phoneticBuffer)
                        .font(.system(size: 18))
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .onSubmit {
                            commitWord()
                        }
                        .onChange(of: phoneticBuffer) { _, newValue in
                            updateSuggestions(for: newValue)
                        }

                    if !phoneticBuffer.isEmpty {
                        Button(action: commitWord) {
                            Image(systemName: "arrow.right.circle.fill")
                                .font(.title2)
                                .foregroundColor(.blue)
                        }
                    }
                }
                .padding()
                .background(Color(.secondarySystemBackground))
            }
            .navigationTitle("Banglu")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: { showSettings = true }) {
                        Image(systemName: "gear")
                    }
                }
                ToolbarItem(placement: .topBarLeading) {
                    Button(action: clearText) {
                        Image(systemName: "trash")
                    }
                    .disabled(bengaliText.isEmpty)
                }
            }
            .sheet(isPresented: $showSettings) {
                SettingsView()
            }
        }
    }

    private func updateSuggestions(for input: String) {
        guard !input.isEmpty else {
            suggestions = []
            return
        }
        // TODO: Use BangluKeyboardEngine.getSuggestions() here
        // For now, placeholder
        suggestions = []
    }

    private func selectSuggestion(_ suggestion: String) {
        bengaliText += suggestion
        phoneticBuffer = ""
        suggestions = []
    }

    private func commitWord() {
        guard !phoneticBuffer.isEmpty else { return }
        // TODO: Use BangluKeyboardEngine.convert() here
        // For now, append the phonetic buffer as-is
        bengaliText += phoneticBuffer + " "
        phoneticBuffer = ""
        suggestions = []
    }

    private func clearText() {
        bengaliText = ""
        phoneticBuffer = ""
        suggestions = []
    }
}

struct SettingsView: View {
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section("Keyboard") {
                    Toggle("Sound on keypress", isOn: .constant(false))
                    Toggle("Haptic feedback", isOn: .constant(true))
                }
                Section("Appearance") {
                    Picker("Font size", selection: .constant(20)) {
                        Text("Small").tag(16)
                        Text("Medium").tag(20)
                        Text("Large").tag(24)
                    }
                }
                Section("About") {
                    HStack {
                        Text("Version")
                        Spacer()
                        Text("1.0.0").foregroundColor(.secondary)
                    }
                    Link("Website", destination: URL(string: "https://banglu.com")!)
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

#Preview {
    ContentView()
}
