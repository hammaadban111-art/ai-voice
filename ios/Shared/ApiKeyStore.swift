import Foundation
import Security

/// Stores the Gemini API key so both the app and the keyboard can read it.
///
/// Android encrypts the key with an AndroidKeyStore AES-GCM key (`api_key_ct` /
/// `api_key_iv` under the alias `voiceappv4_api_key_v1`). The iOS equivalent is
/// a Keychain item in the shared access group: the key material is held by the
/// Secure Enclave-backed keychain, is device-bound (`ThisDeviceOnly`, so it is
/// never carried to another phone by an iCloud restore), and is only readable
/// while the phone is unlocked.
enum ApiKeyStore {

    /// Matches the Android keystore alias so the two builds line up.
    private static let account = "voiceappv4_api_key_v1"
    private static let service = "voiceappv4_secure"
    private static let accessGroup = "$(AppIdentifierPrefix)group.com.hammaad.voiceappv4"

    static var hasKey: Bool { (load()?.isEmpty == false) }

    static func load() -> String? {
        var query = baseQuery()
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess, let data = item as? Data else { return nil }
        guard let key = String(data: data, encoding: .utf8), !key.isEmpty else {
            // Mirrors Android's "Stored API key unreadable, clearing".
            clear()
            return nil
        }
        return key
    }

    @discardableResult
    static func save(_ key: String) -> Bool {
        let trimmed = key.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let data = trimmed.data(using: .utf8) else { return false }

        let query = baseQuery()
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]

        let update = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if update == errSecSuccess { return true }
        guard update == errSecItemNotFound else { return false }

        var insert = query
        insert.merge(attributes) { _, new in new }
        return SecItemAdd(insert as CFDictionary, nil) == errSecSuccess
    }

    static func clear() {
        SecItemDelete(baseQuery() as CFDictionary)
    }

    private static func baseQuery() -> [String: Any] {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        // Keychain sharing is what lets the keyboard extension read the key the
        // container app stored. Simulator builds have no team prefix, so the
        // group is dropped there rather than failing every call.
        #if !targetEnvironment(simulator)
        query[kSecAttrAccessGroup as String] = accessGroup
        #endif
        return query
    }
}
