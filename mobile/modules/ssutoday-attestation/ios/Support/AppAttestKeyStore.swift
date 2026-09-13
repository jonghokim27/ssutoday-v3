import Foundation

struct PendingAppAttestRegistration: Codable, Equatable, Sendable {
  let challenge: String
  let attestation: String
}

struct AppAttestKeyRecord: Codable, Equatable, Sendable {
  let keyId: String
  var registered = false
  var pending: PendingAppAttestRegistration?
}

protocol AppAttestKeyStore {
  func find(studentId: Int) throws -> AppAttestKeyRecord?
  func save(_ record: AppAttestKeyRecord?, studentId: Int) throws
}

// actor에서만 접근한다. 앱 설치 영역에 저장하므로 재설치 시 새 키를 생성하며 백업 복원에서도 제외한다.
final class FileAppAttestKeyStore: AppAttestKeyStore {
  private let directory: URL
  init(directory: URL) { self.directory = directory }
  private func url(_ studentId: Int) throws -> URL {
    guard AppAttestClientData.validStudent(studentId) else { throw AttestFailure.rejected }
    return directory.appendingPathComponent("\(studentId).json")
  }
  func find(studentId: Int) throws -> AppAttestKeyRecord? {
    let file = try url(studentId)
    guard FileManager.default.fileExists(atPath: file.path) else { return nil }
    return try JSONDecoder().decode(AppAttestKeyRecord.self, from: Data(contentsOf: file))
  }
  func save(_ record: AppAttestKeyRecord?, studentId: Int) throws {
    let file = try url(studentId)
    guard let record else {
      if FileManager.default.fileExists(atPath: file.path) { try FileManager.default.removeItem(at: file) }
      return
    }
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    #if os(iOS) || os(macOS)
    var folder = directory
    var values = URLResourceValues()
    values.isExcludedFromBackup = true
    try folder.setResourceValues(values)
    #endif
    let bytes = try JSONEncoder().encode(record)
    #if os(iOS)
    try bytes.write(to: file, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    #else
    try bytes.write(to: file, options: .atomic)
    #endif
  }
}
