import Foundation

protocol AppAttestProvider {
  var isSupported: Bool { get }
  func generateKey() async throws -> String
  func attestKey(_ keyId: String, hash: Data) async throws -> Data
  func generateAssertion(_ keyId: String, hash: Data) async throws -> Data
}

actor AppAttestCoordinator {
  private let provider: AppAttestProvider
  private let keys: AppAttestKeyStore
  private var busy = Set<Int>()

  init(provider: AppAttestProvider, keys: AppAttestKeyStore) { self.provider = provider; self.keys = keys }

  func prepare(studentId: Int) async throws -> AppAttestKeyRecord {
    guard AppAttestClientData.validStudent(studentId) else { throw AttestFailure.rejected }
    guard provider.isSupported else { throw AttestFailure.unavailable }
    if let existing = try keys.find(studentId: studentId) { return existing }
    guard busy.insert(studentId).inserted else { throw AttestFailure.busy }
    defer { busy.remove(studentId) }
    let keyId = try await provider.generateKey()
    guard AppAttestClientData.validKeyId(keyId) else { throw AttestFailure.rejected }
    let record = AppAttestKeyRecord(keyId: keyId)
    try keys.save(record, studentId: studentId)
    return record
  }

  func register(studentId: Int, keyId: String, challenge: String) async throws -> PendingAppAttestRegistration {
    guard provider.isSupported else { throw AttestFailure.unavailable }
    guard var record = try keys.find(studentId: studentId), record.keyId == keyId, !record.registered else { throw AttestFailure.rejected }
    if let pending = record.pending {
      guard pending.challenge == challenge else { throw AttestFailure.rejected }
      return pending
    }
    let hash = AppAttestClientData.hash(try AppAttestClientData.registration(studentId: studentId, challenge: challenge, keyId: keyId))
    guard busy.insert(studentId).inserted else { throw AttestFailure.busy }
    defer { busy.remove(studentId) }
    let attestation: Data
    do { attestation = try await provider.attestKey(keyId, hash: hash) }
    catch {
      if error as? AttestFailure == .invalidKey { try discard(studentId: studentId, keyId: keyId) }
      throw error
    }
    guard try keys.find(studentId: studentId)?.keyId == keyId else { throw AttestFailure.rejected }
    guard !attestation.isEmpty, attestation.count <= 49152 else { throw AttestFailure.rejected }
    let pending = PendingAppAttestRegistration(challenge: challenge, attestation: attestation.base64EncodedString())
    record.pending = pending
    try keys.save(record, studentId: studentId)
    return pending
  }

  func confirm(studentId: Int, keyId: String) throws {
    guard var record = try keys.find(studentId: studentId), record.keyId == keyId else { throw AttestFailure.rejected }
    guard record.registered || record.pending != nil else { throw AttestFailure.rejected }
    record.registered = true
    record.pending = nil
    try keys.save(record, studentId: studentId)
  }

  func discard(studentId: Int, keyId: String) throws {
    if try keys.find(studentId: studentId)?.keyId == keyId { try keys.save(nil, studentId: studentId) }
  }

  func assertPhoto(_ capture: PhotoCaptureStore.Capture, challenge: String) async throws -> (keyId: String, assertion: String) {
    guard provider.isSupported else { throw AttestFailure.unavailable }
    guard let record = try keys.find(studentId: capture.studentId), record.registered else { throw AttestFailure.unavailable }
    let hash = AppAttestClientData.hash(try AppAttestClientData.photo(studentId: capture.studentId, reservationId: capture.reservationId, challenge: challenge, photoHash: capture.photoHash))
    guard busy.insert(capture.studentId).inserted else { throw AttestFailure.busy }
    defer { busy.remove(capture.studentId) }
    let assertion: Data
    do { assertion = try await provider.generateAssertion(record.keyId, hash: hash) }
    catch {
      if error as? AttestFailure == .invalidKey { try discard(studentId: capture.studentId, keyId: record.keyId) }
      throw error
    }
    guard try keys.find(studentId: capture.studentId)?.keyId == record.keyId else { throw AttestFailure.rejected }
    guard !assertion.isEmpty, assertion.count <= 24576 else { throw AttestFailure.rejected }
    return (record.keyId, assertion.base64EncodedString())
  }
}
