import Foundation
import XCTest
@testable import AttestationSupport

final class AttestationSupportTests: XCTestCase {
  func testReservationUsesServerVectorAndRegisteredAccountKey() async throws {
    let data = try AppAttestClientData.reservation(studentId: studentId, roomNo: "1", date: "2026-09-14", startBlock: 20, endBlock: 23, challenge: challenge)
    XCTAssertEqual(base64url(AppAttestClientData.hash(data)), "Id4rRexJVUuXGMctfmccH9sWWo0t_d6yNwA5-N5_yrc")
    XCTAssertThrowsError(try AppAttestClientData.reservation(studentId: studentId, roomNo: "1", date: "2026-02-30", startBlock: 20, endBlock: 23, challenge: challenge))
    XCTAssertThrowsError(try AppAttestClientData.reservation(studentId: studentId, roomNo: "1", date: "2026-09-14", startBlock: 24, endBlock: 23, challenge: challenge))
    let provider = FakeProvider()
    let coordinator = AppAttestCoordinator(provider: provider, keys: MemoryKeys())
    let record = try await coordinator.prepare(studentId: studentId)
    _ = try await coordinator.register(studentId: studentId, keyId: record.keyId, challenge: challenge)
    try await coordinator.confirm(studentId: studentId, keyId: record.keyId)
    let proof = try await coordinator.assertReservation(studentId: studentId, roomNo: "1", date: "2026-09-14", startBlock: 20, endBlock: 23, challenge: challenge)
    XCTAssertEqual(proof.keyId, record.keyId)
    XCTAssertEqual(provider.assertionHash, AppAttestClientData.hash(data))
  }

  func testUnsupportedDevicesAreDistinctFromTemporaryProviderOutages() async throws {
    let provider = FakeProvider()
    provider.isSupported = false
    let coordinator = AppAttestCoordinator(provider: provider, keys: MemoryKeys())
    do { _ = try await coordinator.prepare(studentId: studentId); XCTFail() }
    catch { XCTAssertEqual(error as? AttestFailure, .unsupported) }
    XCTAssertEqual(provider.generated, 0)
  }
  private let studentId = 20260000
  private let challenge = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"
  private let keyId = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="

  func testCanonicalHashesMatchServerAndAndroidVectors() throws {
    let photoHash = AppAttestClientData.photoHash(Data("abc".utf8))
    XCTAssertEqual(photoHash, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    let photo = try AppAttestClientData.photo(studentId: studentId, reservationId: 42, challenge: challenge, photoHash: photoHash)
    XCTAssertEqual(base64url(AppAttestClientData.hash(photo)), "bF7nJGb7o2lT2ogovdDp0VPtyO_xoHxNgqb3OaAcDaU")
    let registration = try AppAttestClientData.registration(studentId: studentId, challenge: challenge, keyId: keyId)
    XCTAssertEqual(base64url(AppAttestClientData.hash(registration)), "pA9dtkbbjzQnPIvkgb0Le6Bjf5ThvCbeXzTpQNLbImY")
    XCTAssertThrowsError(try AppAttestClientData.photo(studentId: studentId, reservationId: 42, challenge: String(challenge.dropLast()) + "9", photoHash: photoHash))
    XCTAssertThrowsError(try AppAttestClientData.registration(studentId: studentId, challenge: challenge, keyId: String(keyId.dropLast())))
  }

  func testCaptureScopeSingleUseExpiryAndInvalidation() throws {
    var time: TimeInterval = 100
    let store = PhotoCaptureStore(now: { time })
    let photo = try store.create(Data("camera".utf8), studentId: studentId, reservationId: 42)
    XCTAssertThrowsError(try store.consume(photo.id, studentId: studentId + 1, reservationId: 42))
    XCTAssertThrowsError(try store.consume(photo.id, studentId: studentId, reservationId: 43))
    XCTAssertEqual(try store.consume(photo.id, studentId: studentId, reservationId: 42), photo)
    XCTAssertThrowsError(try store.consume(photo.id, studentId: studentId, reservationId: 42))
    time += 119.9
    XCTAssertTrue(store.isCurrent(photo))
    time += 0.1
    XCTAssertFalse(store.isCurrent(photo))
    let next = try store.create(Data("next".utf8), studentId: studentId, reservationId: 42)
    store.release(photo.id)
    XCTAssertTrue(store.isCurrent(next))
    store.clear()
    XCTAssertFalse(store.isCurrent(next))
  }

  func testConcurrentCaptureClaimsSucceedOnlyOnce() async throws {
    let store = PhotoCaptureStore()
    let photo = try store.create(Data("camera".utf8), studentId: studentId, reservationId: 42)
    let count = await withTaskGroup(of: Int.self) { group in
      for _ in 0..<8 { group.addTask { (try? store.consume(photo.id, studentId: 20260000, reservationId: 42)) == nil ? 0 : 1 } }
      var total = 0
      for await value in group { total += value }
      return total
    }
    XCTAssertEqual(count, 1)
  }

  func testRegistrationResponseLossAndAccountSeparation() async throws {
    let provider = FakeProvider()
    let keys = MemoryKeys()
    let coordinator = AppAttestCoordinator(provider: provider, keys: keys)
    let first = try await coordinator.prepare(studentId: studentId)
    XCTAssertFalse(first.registered)
    let pending = try await coordinator.register(studentId: studentId, keyId: first.keyId, challenge: challenge)
    let same = try await coordinator.register(studentId: studentId, keyId: first.keyId, challenge: challenge)
    XCTAssertEqual(pending, same)
    XCTAssertEqual(provider.registrations, 1)
    let reopened = AppAttestCoordinator(provider: provider, keys: keys)
    let restored = try await reopened.prepare(studentId: studentId)
    XCTAssertEqual(restored.pending, pending)
    XCTAssertFalse(restored.registered)
    try await reopened.confirm(studentId: studentId, keyId: first.keyId)
    let ready = try await reopened.prepare(studentId: studentId)
    XCTAssertTrue(ready.registered)
    XCTAssertNil(ready.pending)
    let other = try await reopened.prepare(studentId: studentId + 1)
    XCTAssertNotEqual(first.keyId, other.keyId)
    XCTAssertEqual(provider.generated, 2)
  }

  func testRegistrationMustBeConfirmedBeforePhotoAssertionAndUsesNativeHash() async throws {
    let provider = FakeProvider()
    let coordinator = AppAttestCoordinator(provider: provider, keys: MemoryKeys())
    let record = try await coordinator.prepare(studentId: studentId)
    let store = PhotoCaptureStore()
    let capture = try store.create(Data("abc".utf8), studentId: studentId, reservationId: 42)
    do { _ = try await coordinator.assertPhoto(capture, challenge: challenge); XCTFail("Unregistered key used") }
    catch { XCTAssertEqual(error as? AttestFailure, .unavailable) }
    _ = try await coordinator.register(studentId: studentId, keyId: record.keyId, challenge: challenge)
    try await coordinator.confirm(studentId: studentId, keyId: record.keyId)
    let proof = try await coordinator.assertPhoto(capture, challenge: challenge)
    XCTAssertEqual(proof.keyId, record.keyId)
    XCTAssertEqual(base64url(try XCTUnwrap(provider.assertionHash)), "bF7nJGb7o2lT2ogovdDp0VPtyO_xoHxNgqb3OaAcDaU")
  }

  func testProviderOutagePreservesKeyAndInvalidKeyResetsOnlyItsOwner() async throws {
    let provider = FakeProvider()
    let keys = MemoryKeys()
    let coordinator = AppAttestCoordinator(provider: provider, keys: keys)
    let first = try await coordinator.prepare(studentId: studentId)
    let other = try await coordinator.prepare(studentId: studentId + 1)
    provider.failure = .unavailable
    do { _ = try await coordinator.register(studentId: studentId, keyId: first.keyId, challenge: challenge); XCTFail() } catch {}
    XCTAssertEqual(keys.records[studentId]?.keyId, first.keyId)
    provider.failure = .invalidKey
    do { _ = try await coordinator.register(studentId: studentId, keyId: first.keyId, challenge: challenge); XCTFail() } catch {}
    XCTAssertNil(keys.records[studentId])
    XCTAssertEqual(keys.records[studentId + 1]?.keyId, other.keyId)
    provider.failure = nil
    let replacement = try await coordinator.prepare(studentId: studentId)
    try await coordinator.discard(studentId: studentId, keyId: first.keyId)
    XCTAssertEqual(keys.records[studentId]?.keyId, replacement.keyId)
  }

  func testSimultaneousPreparationDoesNotGenerateDuplicateKeys() async throws {
    let provider = FakeProvider()
    provider.slow = true
    let coordinator = AppAttestCoordinator(provider: provider, keys: MemoryKeys())
    await withTaskGroup(of: Void.self) { group in
      for _ in 0..<8 { group.addTask { _ = try? await coordinator.prepare(studentId: 20260000) } }
    }
    XCTAssertEqual(provider.generated, 1)
  }

  func testFileStateSurvivesRelaunchAndResetIsScoped() throws {
    let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    defer { try? FileManager.default.removeItem(at: folder) }
    let keys = FileAppAttestKeyStore(directory: folder)
    let record = AppAttestKeyRecord(keyId: keyId, registered: false, pending: PendingAppAttestRegistration(challenge: challenge, attestation: "YWJj"))
    try keys.save(record, studentId: studentId)
    try keys.save(record, studentId: studentId + 1)
    let reopened = FileAppAttestKeyStore(directory: folder)
    XCTAssertEqual(try reopened.find(studentId: studentId), record)
    try reopened.save(nil, studentId: studentId)
    XCTAssertNil(try reopened.find(studentId: studentId))
    XCTAssertNotNil(try reopened.find(studentId: studentId + 1))
    XCTAssertThrowsError(try reopened.find(studentId: -1))
  }

  private func base64url(_ data: Data) -> String { data.base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "") }
}

private final class MemoryKeys: AppAttestKeyStore {
  var records: [Int: AppAttestKeyRecord] = [:]
  func find(studentId: Int) throws -> AppAttestKeyRecord? { records[studentId] }
  func save(_ record: AppAttestKeyRecord?, studentId: Int) throws { records[studentId] = record }
}

private final class FakeProvider: AppAttestProvider {
  var isSupported = true
  var generated = 0
  var registrations = 0
  var failure: AttestFailure?
  var assertionHash: Data?
  var slow = false
  func generateKey() async throws -> String {
    generated += 1
    if slow { try await Task.sleep(nanoseconds: 10_000_000) }
    return Data(repeating: UInt8(generated), count: 32).base64EncodedString()
  }
  func attestKey(_ keyId: String, hash: Data) async throws -> Data {
    registrations += 1
    if let failure { throw failure }
    return Data("attestation".utf8)
  }
  func generateAssertion(_ keyId: String, hash: Data) async throws -> Data {
    if let failure { throw failure }
    assertionHash = hash
    return Data("assertion".utf8)
  }
}
