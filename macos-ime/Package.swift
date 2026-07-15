// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "BangluIME",
    platforms: [.macOS(.v13)],
    targets: [
        .target(name: "BangluCore"),
        .executableTarget(
            name: "BangluIME",
            dependencies: ["BangluCore"],
            linkerSettings: [
                .linkedFramework("InputMethodKit"),
                .linkedFramework("Cocoa"),
            ]
        ),
        // Test gate on this machine: `swift run BangluCoreTestRunner`.
        // Command-Line-Tools-only Macs (no Xcode) cannot run `swift test` —
        // XCTest.framework is absent and the toolchain's swift-testing helper
        // silently discovers zero tests. Same precedent as
        // ios-keyboard-engine/Tests/RunTests.
        .executableTarget(
            name: "BangluCoreTestRunner",
            dependencies: ["BangluCore"],
            path: "Tests/BangluCoreTestRunner"
        ),
        .testTarget(name: "BangluCoreTests", dependencies: ["BangluCore"]),
    ]
)
