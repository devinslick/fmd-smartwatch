import Foundation
import CArgon2

/// Argon2id via the vendored PHC reference implementation (CArgon2).
/// Parameters fixed by the FMD Server Protocol v2 spec table:
/// t=1, p=4, m=128 MiB, 32-byte hash.
public enum Argon2 {
    public struct Argon2Error: Error, CustomStringConvertible {
        public let code: Int32
        public var description: String { "argon2 error code \(code)" }
    }

    public static func deriveID(
        password: [UInt8],
        salt: [UInt8],
        timeCost: UInt32 = 1,
        memoryKiB: UInt32 = 131_072,
        parallelism: UInt32 = 4,
        hashLength: UInt32 = 32
    ) throws -> [UInt8] {
        var out = [UInt8](repeating: 0, count: Int(hashLength))
        // Raw values: Argon2_id = 2, ARGON2_VERSION_NUMBER (1.3) = 0x13 —
        // avoids depending on imported C-enum constant spellings.
        let type = argon2_type(rawValue: 2)
        let rc = out.withUnsafeMutableBufferPointer { outBuf in
            password.withUnsafeBufferPointer { pwBuf in
                salt.withUnsafeBufferPointer { saltBuf in
                    argon2_hash(
                        timeCost, memoryKiB, parallelism,
                        pwBuf.baseAddress, password.count,
                        saltBuf.baseAddress, salt.count,
                        outBuf.baseAddress, Int(hashLength),
                        nil, 0,
                        type, 0x13
                    )
                }
            }
        }
        guard rc == 0 else { throw Argon2Error(code: rc) }
        return out
    }
}
