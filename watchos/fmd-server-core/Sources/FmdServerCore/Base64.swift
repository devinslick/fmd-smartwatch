import Foundation

/// Byte + base64 helpers. The server emits unpadded base64 on some
/// endpoints; always decode leniently, encode padded (accepted by all).
public enum Base64 {
    public static func decodePadded(_ s: String) -> Data? {
        var b64 = s.trimmingCharacters(in: .whitespacesAndNewlines)
        if b64.count % 4 != 0 {
            b64 += String(repeating: "=", count: (4 - b64.count % 4) % 4)
        }
        return Data(base64Encoded: b64)
    }

    public static func encode(_ d: Data) -> String { d.base64EncodedString() }
}

public extension Data {
    init?(hexEncoded hex: String) {
        var bytes: [UInt8] = []
        bytes.reserveCapacity(hex.count / 2)
        var iter = hex.makeIterator()
        while let hi = iter.next(), let lo = iter.next() {
            guard let h = hi.hexDigitValue, let l = lo.hexDigitValue else { return nil }
            bytes.append(UInt8(h << 4 | l))
        }
        self = Data(bytes)
    }

    var hexEncoded: String { map { String(format: "%02x", $0) }.joined() }
}
