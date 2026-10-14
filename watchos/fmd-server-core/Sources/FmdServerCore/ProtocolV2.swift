import Foundation
import Crypto

/// FMD Server Protocol v2 cryptography — mirrors gitlab.com/fmd-foss/server-protocol
/// and the reference implementations (fmd-android CryptoV2/LongTermKeys,
/// python fmd_api protocol_v2.py).
///
/// Key schedule: Argon2id(p) → K_pwk → HKDF-SHA256 → (K_auth, K_pmk);
/// account master key wrapped under K_pmk; per-type KEKs via HKDF(master).
/// Wire format of an encrypted data item's ciphertext:
///
///     encrypted_dek   = iv(12) || ct(32) || tag(16)   AES-GCM under K_tau
///     encrypted_data  = iv(12) || ct(n)  || tag(16)   AES-GCM under DEK
///     ciphertext      = encrypted_dek || encrypted_data
///
/// Every AES-GCM call binds associated data:
///     context || typeLabel || SHA256(username) || itemId(16) || millisBE(8)
public enum ProtocolV2 {

    public enum V2Error: Error, CustomStringConvertible {
        case badCiphertext
        case unknownDataType(String)
        public var description: String {
            switch self {
            case .badCiphertext: return "protocol v2: ciphertext/AuthTag mismatch"
            case .unknownDataType(let t): return "unknown data type: \(t)"
            }
        }
    }

    // Context strings (protocol v2)
    static let ctxPassword = Data("fmd_v2_password".utf8)
    static let ctxAuth = Data("fmd_v2_auth".utf8)
    static let ctxPremaster = Data("fmd_v2_premaster".utf8)
    static let ctxMaster = Data("fmd_v2_master".utf8)
    static let ctxKekPrefix = Data("fmd_v2_kek_".utf8)
    static let ctxDekPrefix = Data("fmd_v2_dek_".utf8)
    static let ctxDataPrefix = Data("fmd_v2_data_".utf8)

    // Sizes (bytes)
    public static let ivSize = 12
    public static let keySize = 32
    public static let tagSize = 16
    public static let itemIdSize = 16

    /// Argon2id parameters from the spec table.
    public static let argon2TimeCost: UInt32 = 1
    public static let argon2Parallelism: UInt32 = 4
    public static let argon2MemoryKiB: UInt32 = 131_072
}

// MARK: - Password keys

/// The pair derived from the passphrase at login.
public struct PasswordKeys {
    public let authKey: SymmetricKey        // sent (b64) as passwordHash64
    public let preMasterKey: SymmetricKey   // unwraps encMasterKey64
    public init(authKey: SymmetricKey, preMasterKey: SymmetricKey) {
        self.authKey = authKey
        self.preMasterKey = preMasterKey
    }
}

public extension ProtocolV2 {

    static func sha256(_ s: String) -> Data {
        Data(Crypto.SHA256.hash(data: Data(s.utf8)))
    }

    /// HKDF-SHA256 extract+expand, empty salt — matches BouncyCastle
    /// HKDFParameters(ikm, null, info) and python HKDF(salt=None).
    /// (HMAC zero-pads keys, so nil and empty salt derive the same PRK.)
    static func hkdf(_ ikm: Data, info: Data, length: Int = 32) -> SymmetricKey {
        HKDF<SHA256>.deriveKey(
            inputKeyMaterial: SymmetricKey(data: ikm),
            salt: Data(),
            info: info,
            outputByteCount: length
        )
    }

    /// K_pwk = Argon2id("fmd_v2_password" || SHA256(U) || P, salt)
    /// K_auth = HKDF(K_pwk, info="fmd_v2_auth"     || SHA256(U))
    /// K_pmk  = HKDF(K_pwk, info="fmd_v2_premaster"|| SHA256(U))
    static func derivePasswordKeys(username: String, password: String, salt: Data) throws -> PasswordKeys {
        let userHash = sha256(username)
        var secret = ctxPassword
        secret.append(userHash)
        secret.append(Data(password.utf8))
        let pwk = try Argon2.deriveID(
            password: [UInt8](secret),
            salt: [UInt8](salt),
            timeCost: argon2TimeCost,
            memoryKiB: argon2MemoryKiB,
            parallelism: argon2Parallelism,
            hashLength: 32
        )
        var authInfo = ctxAuth; authInfo.append(userHash)
        var pmkInfo = ctxPremaster; pmkInfo.append(userHash)
        let pwkData = Data(pwk)
        return PasswordKeys(
            authKey: hkdf(pwkData, info: authInfo),
            preMasterKey: hkdf(pwkData, info: pmkInfo)
        )
    }
}

// MARK: - Master key

public extension ProtocolV2 {

    /// Decrypt the account master key (login's encMasterKey64 payload,
    /// iv||ct||tag under K_pmk, AD = "fmd_v2_master" || SHA256(U)).
    static func decryptMasterKey(
        username: String, preMasterKey: SymmetricKey, encryptedMasterKey: Data
    ) throws -> SymmetricKey {
        var ad = ctxMaster
        ad.append(sha256(username))
        let plain = try openAEAD(encryptedMasterKey, key: preMasterKey, ad: ad)
        guard plain.count == keySize else { throw V2Error.badCiphertext }
        return SymmetricKey(data: plain)
    }

    /// Wrap a fresh master key under K_pmk (registration/rotation);
    /// returns iv || ct || tag for encMasterKey64.
    static func encryptMasterKey(
        username: String, preMasterKey: SymmetricKey, masterKey: SymmetricKey
    ) throws -> Data {
        var ad = ctxMaster
        ad.append(sha256(username))
        let mk = masterKey.withUnsafeBytes { Data($0) }
        return try sealAEAD(mk, key: preMasterKey, ad: ad)
    }

    static func generateMasterKey() -> SymmetricKey {
        SymmetricKey(size: .bits256)
    }
}

// MARK: - Per-type KEKs + data envelopes

public enum FmdDataType: String, CaseIterable {
    case location
    case picture
    case command

    var label: Data { Data(self.rawValue.utf8) }
}

public struct FmdEncryptedItem {
    public let itemID: Data          // 16 bytes, client-chosen
    public let unixMillis: Int64
    public let ciphertext: Data      // [iv|dek|tag][iv|data|tag]
    public init(itemID: Data, unixMillis: Int64, ciphertext: Data) {
        self.itemID = itemID
        self.unixMillis = unixMillis
        self.ciphertext = ciphertext
    }
}

/// Per-type KEKs derived from the account master key.
public struct LongTermKeys {
    public let username: String
    public let masterKey: SymmetricKey
    public let keks: [FmdDataType: SymmetricKey]

    public init(username: String, masterKey: SymmetricKey) throws {
        self.username = username
        self.masterKey = masterKey
        var keks: [FmdDataType: SymmetricKey] = [:]
        for type in FmdDataType.allCases {
            var info = ProtocolV2.ctxKekPrefix
            info.append(Data(type.rawValue.utf8))
            info.append(ProtocolV2.sha256(username))
            keks[type] = ProtocolV2.hkdf(
                masterKey.withUnsafeBytes { Data($0) }, info: info
            )
        }
        self.keks = keks
    }

    func kek(_ type: FmdDataType) throws -> SymmetricKey {
        guard let k = keks[type] else {
            throw ProtocolV2.V2Error.unknownDataType(type.rawValue)
        }
        return k
    }

    /// AD suffix: typeLabel || SHA256(U) || itemID || millisBE(8)
    private func adSuffix(_ type: FmdDataType, itemID: Data, millis: Int64) -> Data {
        var ad = type.label
        ad.append(ProtocolV2.sha256(username))
        ad.append(itemID)
        var be = UInt64(millis).bigEndian
        withUnsafeBytes(of: &be) { ad.append(contentsOf: $0) }
        return ad
    }

    /// Encrypt one data item: fresh 128-bit id, fresh DEK, AES-GCM twice.
    public func encrypt(_ raw: Data, type: FmdDataType) throws -> FmdEncryptedItem {
        let itemID = Data((0..<ProtocolV2.itemIdSize).map { _ in UInt8.random(in: .min ... .max) })
        let millis = Int64(Date().timeIntervalSince1970 * 1000)
        let dek = SymmetricKey(size: .bits256)
        var ad = ProtocolV2.ctxDekPrefix
        ad.append(adSuffix(type, itemID: itemID, millis: millis))
        let encDek = try ProtocolV2.sealAEAD(
            dek.withUnsafeBytes { Data($0) }, key: try kek(type), ad: ad
        )
        var adData = ProtocolV2.ctxDataPrefix
        adData.append(adSuffix(type, itemID: itemID, millis: millis))
        let encData = try ProtocolV2.sealAEAD(raw, key: dek, ad: adData)
        return FmdEncryptedItem(
            itemID: itemID,
            unixMillis: millis,
            ciphertext: encDek + encData
        )
    }

    /// Decrypt one data envelope. Does not validate freshness; callers
    /// apply command freshness rules (mirrors the Android reference).
    public func decrypt(_ item: FmdEncryptedItem, type: FmdDataType) throws -> Data {
        let dekEnd = ProtocolV2.ivSize + ProtocolV2.keySize + ProtocolV2.tagSize
        guard item.ciphertext.count > dekEnd else {
            throw ProtocolV2.V2Error.badCiphertext
        }
        let encDek = item.ciphertext.prefix(dekEnd)
        let encData = item.ciphertext.suffix(from: dekEnd)
        var ad = ProtocolV2.ctxDekPrefix
        ad.append(adSuffix(type, itemID: item.itemID, millis: item.unixMillis))
        let dek = try ProtocolV2.openAEAD(Data(encDek), key: try kek(type), ad: ad)
        var adData = ProtocolV2.ctxDataPrefix
        adData.append(adSuffix(type, itemID: item.itemID, millis: item.unixMillis))
        return try ProtocolV2.openAEAD(Data(encData), key: SymmetricKey(data: dek), ad: adData)
    }
}

// MARK: - AES-GCM wire helpers (iv || ct || tag)

enum AEAD {
    static func seal(_ plaintext: Data, key: SymmetricKey, nonce: AES.GCM.Nonce, ad: Data) throws -> Data {
        let box = try AES.GCM.seal(plaintext, using: key, nonce: nonce, authenticating: ad)
        guard let combined = box.combined else {
            throw ProtocolV2.V2Error.badCiphertext
        }
        return combined  // nonce || ct || tag
    }
}

public extension ProtocolV2 {
    static func sealAEAD(_ plaintext: Data, key: SymmetricKey, ad: Data) throws -> Data {
        try AEAD.seal(plaintext, key: key, nonce: AES.GCM.Nonce(), ad: ad)
    }

    static func sealAEAD(_ plaintext: Data, key: SymmetricKey, nonce: AES.GCM.Nonce, ad: Data) throws -> Data {
        try AEAD.seal(plaintext, key: key, nonce: nonce, ad: ad)
    }

    static func openAEAD(_ blob: Data, key: SymmetricKey, ad: Data) throws -> Data {
        let nonce = try AES.GCM.Nonce(data: blob.prefix(ivSize))
        let box = try AES.GCM.SealedBox(
            nonce: nonce,
            ciphertext: blob.suffix(from: ivSize).dropLast(tagSize),
            tag: blob.suffix(tagSize)
        )
        return try AES.GCM.open(box, using: key, authenticating: ad)
    }
}
