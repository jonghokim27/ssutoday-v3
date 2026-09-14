import ExpoModulesCore
import Foundation

public class SsutodayAttestationModule: Module {
  private let captures = PhotoCaptureStore()
  private let coordinator: AppAttestCoordinator = {
    let environment = Bundle.main.object(forInfoDictionaryKey: "SSUTODAYAppAttestEnvironment") as? String ?? "production"
    let folder = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
      .appendingPathComponent("SSUTODAY/AppAttest/\(environment)")
    return AppAttestCoordinator(provider: DeviceCheckProvider(), keys: FileAppAttestKeyStore(directory: folder))
  }()

  public func definition() -> ModuleDefinition {
    Name("SsutodayAttestation")
    Function("createBridgeToken") { UUID().uuidString + UUID().uuidString }
    Function("clearCaptures") { self.captures.clear() }
    Function("releaseCapture") { (id: String) in self.captures.release(id) }

    AsyncFunction("prepareAppAttest") { (studentId: Int) async throws -> [String: Any] in
      do {
        let record = try await self.coordinator.prepare(studentId: studentId)
        var result: [String: Any] = ["keyId": record.keyId, "registered": record.registered]
        if let pending = record.pending { result["pending"] = ["challenge": pending.challenge, "attestation": pending.attestation] }
        return result
      } catch { throw self.bridgeError(error) }
    }
    AsyncFunction("attestRegister") { (studentId: Int, keyId: String, challenge: String) async throws -> [String: String] in
      do {
        let pending = try await self.coordinator.register(studentId: studentId, keyId: keyId, challenge: challenge)
        return ["keyId": keyId, "challenge": pending.challenge, "attestation": pending.attestation]
      } catch { throw self.bridgeError(error) }
    }
    AsyncFunction("confirmAppAttest") { (studentId: Int, keyId: String) async throws in
      do { try await self.coordinator.confirm(studentId: studentId, keyId: keyId) }
      catch { throw self.bridgeError(error) }
    }
    AsyncFunction("resetAppAttest") { (studentId: Int, keyId: String) async throws in
      do { try await self.coordinator.discard(studentId: studentId, keyId: keyId) }
      catch { throw self.bridgeError(error) }
    }

    // 이 메서드는 RN 촬영 완료 핸들러에서만 부른다. 웹에는 파일 등록 경로를 제공하지 않는다.
    AsyncFunction("storeCapture") { (uri: String, studentId: Int, reservationId: Int64) throws -> [String: String] in
      do {
        guard let url = URL(string: uri), url.isFileURL else { throw AttestFailure.rejected }
        let file = url.resolvingSymlinksInPath().standardizedFileURL
        let cache = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].resolvingSymlinksInPath().standardizedFileURL.path + "/"
        guard file.path.hasPrefix(cache) else { throw AttestFailure.rejected }
        let size = try file.resourceValues(forKeys: [.fileSizeKey, .isRegularFileKey])
        guard size.isRegularFile == true, let count = size.fileSize, (3...10_485_760).contains(count) else { throw AttestFailure.rejected }
        let data = try Data(contentsOf: file)
        guard (3...10_485_760).contains(data.count), data[0] == 0xff, data[1] == 0xd8 else { throw AttestFailure.rejected }
        let capture = try self.captures.create(data, studentId: studentId, reservationId: reservationId)
        return ["captureId": capture.id, "photoSha256": capture.photoHash, "uri": "data:image/jpeg;base64,\(data.base64EncodedString())"]
      } catch { throw self.bridgeError(error) }
    }
    AsyncFunction("attest") { (captureId: String, studentId: Int, reservationId: Int64, challenge: String) async throws -> [String: String] in
      do {
        let capture = try self.captures.consume(captureId, studentId: studentId, reservationId: reservationId)
        let proof = try await self.coordinator.assertPhoto(capture, challenge: challenge)
        guard self.captures.isCurrent(capture) else { throw AttestFailure.rejected }
        return ["platform": "ios", "keyId": proof.keyId, "attestation": proof.assertion]
      } catch { throw self.bridgeError(error) }
    }
    AsyncFunction("attestReservation") { (studentId: Int, roomNo: String, date: String, startBlock: Int, endBlock: Int, challenge: String) async throws -> [String: String] in
      do {
        let proof = try await self.coordinator.assertReservation(studentId: studentId, roomNo: roomNo, date: date, startBlock: startBlock, endBlock: endBlock, challenge: challenge)
        return ["platform": "ios", "keyId": proof.keyId, "attestation": proof.assertion]
      } catch { throw self.bridgeError(error) }
    }
    OnDestroy { self.captures.clear() }
  }

  private func bridgeError(_ error: Error) -> Exception {
    let failure = error as? AttestFailure
    let code: String
    switch failure {
    case .unsupported: code = "ERR_ATTESTATION_UNSUPPORTED"
    case .unavailable, .none: code = "ERR_INTEGRITY_UNAVAILABLE"
    case .invalidKey: code = "ERR_APP_ATTEST_KEY_INVALID"
    default: code = "ERR_CAPTURE_REJECTED"
    }
    return Exception(name: "AppAttestError", description: "앱 무결성 증명을 생성하지 못했습니다", code: code)
  }
}
