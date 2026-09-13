import DeviceCheck
import Foundation

final class DeviceCheckProvider: AppAttestProvider {
  var isSupported: Bool { DCAppAttestService.shared.isSupported }

  func generateKey() async throws -> String {
    try await operation(timeout: 25, registration: false) { completion in
      DCAppAttestService.shared.generateKey(completionHandler: completion)
    }
  }
  func attestKey(_ keyId: String, hash: Data) async throws -> Data {
    try await operation(timeout: 45, registration: true) { completion in
      DCAppAttestService.shared.attestKey(keyId, clientDataHash: hash, completionHandler: completion)
    }
  }
  func generateAssertion(_ keyId: String, hash: Data) async throws -> Data {
    try await operation(timeout: 25, registration: false) { completion in
      DCAppAttestService.shared.generateAssertion(keyId, clientDataHash: hash, completionHandler: completion)
    }
  }

  private func operation<T>(timeout: TimeInterval, registration: Bool, start: (@escaping (T?, Error?) -> Void) -> Void) async throws -> T {
    try await withCheckedThrowingContinuation { continuation in
      let result = TimedAppAttestResult<T>(continuation)
      result.startTimeout(after: timeout)
      start { value, error in
        if let error {
          let dcError = error as NSError
          let invalid = dcError.domain == DCError.errorDomain && dcError.code == DCError.invalidKey.rawValue
          let unavailable = dcError.domain == DCError.errorDomain && dcError.code == DCError.serverUnavailable.rawValue
          result.finish(.failure(invalid || (registration && !unavailable) ? AttestFailure.invalidKey : AttestFailure.unavailable))
        } else if let value {
          result.finish(.success(value))
        } else {
          result.finish(.failure(AttestFailure.unavailable))
        }
      }
    }
  }
}

// DeviceCheck 콜백이 제한 시간 뒤에 와도 continuation은 한 번만 완료한다.
private final class TimedAppAttestResult<T>: @unchecked Sendable {
  private let lock = NSLock()
  private var continuation: CheckedContinuation<T, Error>?
  private var timer: DispatchWorkItem?
  init(_ continuation: CheckedContinuation<T, Error>) { self.continuation = continuation }
  func startTimeout(after seconds: TimeInterval) {
    let work = DispatchWorkItem { [self] in finish(.failure(AttestFailure.unavailable)) }
    lock.lock(); timer = work; lock.unlock()
    DispatchQueue.global().asyncAfter(deadline: .now() + seconds, execute: work)
  }
  func finish(_ result: Result<T, Error>) {
    lock.lock()
    let waiting = continuation
    continuation = nil
    let pendingTimer = timer
    timer = nil
    lock.unlock()
    pendingTimer?.cancel()
    waiting?.resume(with: result)
  }
}
