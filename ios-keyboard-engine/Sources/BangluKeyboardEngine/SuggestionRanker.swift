import Foundation

public struct RankedSuggestion: Sendable {
    public let bengali: String
    public let confidence: Double
    public let phonetic: String

    public init(bengali: String, confidence: Double, phonetic: String = "") {
        self.bengali = bengali
        self.confidence = confidence
        self.phonetic = phonetic
    }
}

public enum SuggestionRanker {
    public static func rank(
        results: [(bengali: String, phonetic: String, frequency: Int)],
        inputPhonetic: String
    ) -> [RankedSuggestion] {
        return results.map { r in
            let confidence: Double
            if r.phonetic.lowercased() == inputPhonetic.lowercased() {
                confidence = min(0.90 + Double(r.frequency) / 1000.0, 1.0)
            } else {
                let ratio = Double(inputPhonetic.count) / max(Double(r.phonetic.count), 1.0)
                confidence = min(0.50 + ratio * 0.30 + Double(r.frequency) / 1000.0, 1.0)
            }
            return RankedSuggestion(bengali: r.bengali, confidence: confidence, phonetic: r.phonetic)
        }.sorted { $0.confidence > $1.confidence }
    }
}
