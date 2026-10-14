// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "fmd-server-core",
    products: [
        .library(name: "FmdServerCore", targets: ["FmdServerCore"]),
    ],
    dependencies: [
        .package(url: "https://github.com/apple/swift-crypto.git", from: "3.10.0"),
    ],
    targets: [
        .target(
            name: "CArgon2",
            path: "Sources/CArgon2",
            publicHeadersPath: "include"
        ),
        .target(
            name: "FmdServerCore",
            dependencies: ["CArgon2", .product(name: "Crypto", package: "swift-crypto")],
            path: "Sources/FmdServerCore"
        ),
        .testTarget(
            name: "FmdServerCoreTests",
            dependencies: ["FmdServerCore"],
            path: "Tests/FmdServerCoreTests",
            resources: [.copy("Fixtures")]
        ),
    ]
)
