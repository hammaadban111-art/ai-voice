import Combine
import Foundation

/// One saved dictation. Field-for-field the same record the Android build keeps.
struct TranscriptEntry: Codable, Identifiable, Equatable {
    var id: String = UUID().uuidString
    var text: String
    var createdAt: Date
    var appLabel: String?
    var model: String
    var viaFallback: Bool
    var durationMs: Int
}

/// History, shared between the keyboard (which writes) and the app (which shows
/// and deletes). A JSON file in the App Group container rather than
/// `UserDefaults`, because transcripts grow and defaults are not a database.
final class TranscriptStore: ObservableObject {

    static let shared = TranscriptStore()
    private static let limit = 200

    @Published private(set) var entries: [TranscriptEntry] = []

    private let url: URL?
    private let lock = NSLock()

    init(url: URL? = nil) {
        self.url = url ?? FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: SettingsStore.appGroup)?
            .appendingPathComponent("history.json")
        reload()
    }

    func reload() {
        guard let url, let data = try? Data(contentsOf: url) else {
            entries = []
            return
        }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        // A truncated file (killed mid-write) should cost the history, not the app.
        entries = (try? decoder.decode([TranscriptEntry].self, from: data)) ?? []
    }

    func add(_ entry: TranscriptEntry) {
        lock.lock()
        reload()
        var next = entries
        next.insert(entry, at: 0)
        if next.count > Self.limit { next.removeLast(next.count - Self.limit) }
        entries = next
        persist()
        lock.unlock()
    }

    func delete(_ entry: TranscriptEntry) {
        lock.lock()
        entries.removeAll { $0.id == entry.id }
        persist()
        lock.unlock()
    }

    func clear() {
        lock.lock()
        entries = []
        persist()
        lock.unlock()
    }

    private func persist() {
        guard let url else { return }
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        guard let data = try? encoder.encode(entries) else { return }
        try? FileManager.default.createDirectory(
            at: url.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        // Write to a sibling first: a keyboard extension can be killed at any
        // moment, and a half-written history file would lose everything.
        let staging = url.appendingPathExtension("part")
        do {
            try data.write(to: staging, options: .atomic)
            _ = try FileManager.default.replaceItemAt(url, withItemAt: staging)
        } catch {
            try? data.write(to: url, options: .atomic)
        }
    }
}
