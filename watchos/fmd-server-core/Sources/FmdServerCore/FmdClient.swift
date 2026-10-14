import Foundation
import Crypto
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// FMD Server API v2 client (protocol v2 accounts).
///
/// Flow: salt → Argon2id/HKDF → login (passwordHash64 = b64(K_auth)) →
/// unwrap encMasterKey64 → per-type KEKs → commands as encrypted data
/// items on POST /api/v2/data/command; results from GET /api/v2/data/{type}.
///
/// Reference: gitlab.com/fmd-foss/fmd-server openapi.yaml + the python
/// fmd_api client. Network calls use URLSession (async/await) — the
/// crypto layer is testable without a server (see Tests).
public final class FmdClient {

    public enum ClientError: Error, CustomStringConvertible {
        case http(Int, String)
        case notLoggedIn
        case badResponse
        public var description: String {
            switch self {
            case .http(let code, let body): return "HTTP \(code): \(body.prefix(200))"
            case .notLoggedIn: return "not logged in"
            case .badResponse: return "malformed server response"
            }
        }
    }

    /// Authenticated protocol-v2 session state.
    public struct Session {
        public let username: String
        public let accessToken: String
        public let keys: LongTermKeys
        /// K_auth — enables re-login on token expiry (b64 of this is sent again).
        public let authKey: SymmetricKey
    }

    public let baseURL: URL
    private let session: URLSession
    private var active: Session?
    public var sessionDurationSeconds: Int = 3_600

    public init(baseURL: URL) {
        self.baseURL = baseURL
        self.session = URLSession(configuration: .ephemeral)
    }

    // MARK: - Wire DTOs

    struct LoginRequest: Encodable {
        let username: String
        let passwordHash64: String
        let sessionDurationSeconds: Int
    }

    struct AuthResponse: Decodable {
        let accessToken: String?
        let encMasterKey64: String?
    }

    struct SaltResponse: Decodable {
        let protoVersion: Int?
        let salt64: String
    }

    struct ItemDTO: Codable {
        let clientItemIdHex: String
        let unixMillis: Int64
        let ciphertext64: String
    }

    struct ItemsRequest: Encodable {
        let items: [ItemDTO]
    }

    struct ItemsResponse: Decodable {
        let items: [ItemDTO]?
    }

    // MARK: - Auth

    /// GET /account/{name}/salt — also probes protocol version (404 ⇒ v1 server).
    public func fetchSalt(username: String) async throws -> (protoVersion: Int, salt: Data) {
        let resp: SaltResponse = try await request(
            "GET", "/account/\(username)/salt", body: Optional<LoginRequest>.none
        )
        guard let salt = Base64.decodePadded(resp.salt64) else { throw ClientError.badResponse }
        return (resp.protoVersion ?? 1, salt)
    }

    /// Full protocol-v2 login: salt → derive → login → unwrap master key.
    @discardableResult
    public func login(username: String, password: String) async throws -> Session {
        let (_, salt) = try await fetchSalt(username: username)
        let keys = try ProtocolV2.derivePasswordKeys(
            username: username, password: password, salt: salt
        )
        let authB64 = keys.authKey.withUnsafeBytes { Data($0).base64EncodedString() }
        let resp: AuthResponse = try await request(
            "POST", "/account/login", body: LoginRequest(
                username: username, passwordHash64: authB64,
                sessionDurationSeconds: sessionDurationSeconds
            )
        )
        guard let token = resp.accessToken, let emk64 = resp.encMasterKey64,
              let emk = Base64.decodePadded(emk64)
        else {
            // Missing encMasterKey64 = protocol-v1 account on a v2 server.
            throw ClientError.badResponse
        }
        let masterKey = try ProtocolV2.decryptMasterKey(
            username: username, preMasterKey: keys.preMasterKey, encryptedMasterKey: emk
        )
        let s = Session(
            username: username, accessToken: token,
            keys: try LongTermKeys(username: username, masterKey: masterKey),
            authKey: keys.authKey
        )
        active = s
        return s
    }

    // MARK: - Commands + data

    /// Send one command string ("ring", "locate gps", "lock msg"...).
    /// v2 accounts: no RSA — encrypted under the command-type KEK and
    /// uploaded as a data item (mirrors fmd-android FmdServerApiV2Repository).
    public func sendCommand(_ command: String) async throws {
        let s = try requireSession()
        let item = try s.keys.encrypt(Data(command.utf8), type: .command)
        let dto = ItemDTO(
            clientItemIdHex: item.itemID.hexEncoded,
            unixMillis: item.unixMillis,
            ciphertext64: Base64.encode(item.ciphertext)
        )
        let _: EmptyResponse? = try await request(
            "POST", "/data/command", body: ItemsRequest(items: [dto]), auth: true
        )
    }

    /// Fetch + decrypt all items of a type, newest first.
    public func getDataItems(_ type: FmdDataType) async throws -> [(item: FmdEncryptedItem, plaintext: Data)] {
        let s = try requireSession()
        let resp: ItemsResponse = try await request(
            "GET", "/data/\(type.rawValue)", body: Optional<LoginRequest>.none, auth: true
        )
        var out: [(FmdEncryptedItem, Data)] = []
        for dto in resp.items ?? [] {
            guard let id = Data(hexEncoded: dto.clientItemIdHex),
                  let ct = Base64.decodePadded(dto.ciphertext64)
            else { continue }
            let item = FmdEncryptedItem(
                itemID: id, unixMillis: dto.unixMillis, ciphertext: ct
            )
            let plain = try s.keys.decrypt(item, type: type)
            out.append((item, plain))
        }
        out.sort { $0.0.unixMillis > $1.0.unixMillis }
        return out
    }

    /// Locations, newest first, as parsed fixes.
    public func getLocations() async throws -> [FmdLocation] {
        try await getDataItems(.location).compactMap { _, plain in
            try? FmdLocation(jsonData: plain)
        }
    }

    // MARK: - Plumbing

    struct EmptyResponse: Decodable {}

    private func requireSession() throws -> Session {
        guard let s = active else { throw ClientError.notLoggedIn }
        return s
    }

    private func request<Body: Encodable, Resp: Decodable>(
        _ method: String, _ path: String, body: Body?, auth: Bool = false
    ) async throws -> Resp {
        var req = URLRequest(url: baseURL.appendingPathComponent("api/v2" + path))
        req.httpMethod = method
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if auth, let s = active {
            req.setValue("Bearer \(s.accessToken)", forHTTPHeaderField: "Authorization")
        }
        if let body {
            req.httpBody = try JSONEncoder().encode(body)
        }
        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await session.data(for: req)
        } catch {
            throw ClientError.http(-1, String(describing: error))
        }
        guard let http = response as? HTTPURLResponse else { throw ClientError.badResponse }
        guard (200..<300).contains(http.statusCode) else {
            throw ClientError.http(http.statusCode, String(data: data, encoding: .utf8) ?? "")
        }
        guard !data.isEmpty else {
            // 2xx with empty body (e.g. POST /data/{type})
            if Resp.self == EmptyResponse.self { return EmptyResponse() as! Resp }
            throw ClientError.badResponse
        }
        return try JSONDecoder().decode(Resp.self, from: data)
    }
}
