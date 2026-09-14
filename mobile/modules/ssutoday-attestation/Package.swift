// swift-tools-version: 5.9
import PackageDescription

let package = Package(
  name: "AttestationSupport",
  platforms: [.macOS(.v12), .iOS(.v15)],
  products: [.library(name: "AttestationSupport", targets: ["AttestationSupport"])],
  dependencies: [.package(url: "https://github.com/apple/swift-crypto.git", exact: "3.15.1")],
  targets: [
    .target(name: "AttestationSupport", dependencies: [.product(name: "Crypto", package: "swift-crypto")], path: "ios/Support"),
    .testTarget(name: "AttestationSupportTests", dependencies: ["AttestationSupport"], path: "tests")
  ]
)
