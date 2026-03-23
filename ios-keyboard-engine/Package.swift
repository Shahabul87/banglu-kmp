// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "BangluKeyboardEngine",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [
        .library(name: "BangluKeyboardEngine", targets: ["BangluKeyboardEngine"]),
        .executable(name: "RunTests", targets: ["RunTests"]),
    ],
    targets: [
        .target(name: "BangluKeyboardEngine"),
        .executableTarget(
            name: "RunTests",
            dependencies: ["BangluKeyboardEngine"],
            path: "Tests/RunTests"
        ),
        .testTarget(
            name: "BangluKeyboardEngineTests",
            dependencies: ["BangluKeyboardEngine"]
        ),
    ]
)
