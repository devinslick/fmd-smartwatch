import Foundation

/// A decrypted location fix (payload of a location-type data item).
/// Mirrors fmd_api models.Location — required lat/lon/date, lenient
/// optional numerics (unusable optionals become nil, not failures).
public struct FmdLocation: Equatable, Sendable {
    public let lat: Double
    public let lon: Double
    public let dateMillis: Int64
    public var accuracy: Double?
    public var altitude: Double?
    public var speed: Double?
    public var heading: Double?
    public var battery: Double?
    public var provider: String?

    public init(lat: Double, lon: Double, dateMillis: Int64,
                accuracy: Double? = nil, altitude: Double? = nil,
                speed: Double? = nil, heading: Double? = nil,
                battery: Double? = nil, provider: String? = nil) {
        self.lat = lat
        self.lon = lon
        self.dateMillis = dateMillis
        self.accuracy = accuracy
        self.altitude = altitude
        self.speed = speed
        self.heading = heading
        self.battery = battery
        self.provider = provider
    }

    private static func finite(_ v: Double?) -> Double? {
        guard let v, v.isFinite else { return nil }
        return v
    }

    public init(jsonData: Data) throws {
        guard let obj = try JSONSerialization.jsonObject(with: jsonData) as? [String: Any],
              let latRaw = obj["lat"], let latVal = FmdLocation.doubleValue(latRaw),
              let lonRaw = obj["lon"], let lonVal = FmdLocation.doubleValue(lonRaw),
              latVal.isFinite, lonVal.isFinite,
              abs(latVal) <= 90, abs(lonVal) <= 180,
              let dateVal = FmdLocation.doubleValue(obj["date"])
        else {
            throw DecodingError.dataCorrupted(.init(
                codingPath: [], debugDescription: "invalid location payload"))
        }
        self.lat = latVal
        self.lon = lonVal
        self.dateMillis = Int64(dateVal)
        self.accuracy = Self.finite(FmdLocation.doubleValue(obj["accuracy"]))
        self.altitude = Self.finite(FmdLocation.doubleValue(obj["altitude"]))
        self.speed = Self.finite(FmdLocation.doubleValue(obj["speed"]))
        self.heading = Self.finite(FmdLocation.doubleValue(obj["heading"]))
        self.battery = Self.finite(FmdLocation.doubleValue(obj["bat"]))
        if let p = obj["provider"] as? String { self.provider = p }
    }

    private static func doubleValue(_ any: Any) -> Double? {
        if let d = any as? Double { return d }
        if let i = any as? Int { return Double(i) }
        if let i64 = any as? Int64 { return Double(i64) }
        if let n = any as? NSNumber { return n.doubleValue }
        return nil
    }
}

/// FMD command strings for the server transport (vocabulary identical to
/// the fmd_api reference client and the notification commands).
public enum FmdCommand {
    public static let ring = "ring"

    public enum LocationProvider: String {
        case all = "locate"
        case gps = "locate gps"
        case cell = "locate cell"
        case network = "locate network"
        case last = "locate last"
    }

    public enum Camera: String {
        case front = "camera front"
        case back = "camera back"
    }

    public enum RingerMode: String {
        case normal = "ringermode normal"
        case vibrate = "ringermode vibrate"
        case silent = "ringermode silent"
    }

    public static func lock(message: String? = nil) -> String {
        // Server executes the base command even if it ignores the payload;
        // mirror fmd_api by stripping quotes/newlines from the message.
        guard var msg = message?.trimmingCharacters(in: .whitespacesAndNewlines),
              !msg.isEmpty else { return "lock" }
        msg = msg
            .replacingOccurrences(of: "\"", with: "")
            .replacingOccurrences(of: "\n", with: " ")
            .replacingOccurrences(of: "\r", with: " ")
        let collapsed = msg.split(whereSeparator: \.isWhitespace).joined(separator: " ")
        guard !collapsed.isEmpty else { return "lock" }
        return "lock \(collapsed)"
    }

    public static func bluetooth(_ on: Bool) -> String { on ? "bluetooth on" : "bluetooth off" }
    public static func doNotDisturb(_ on: Bool) -> String { on ? "nodisturb on" : "nodisturb off" }
}
