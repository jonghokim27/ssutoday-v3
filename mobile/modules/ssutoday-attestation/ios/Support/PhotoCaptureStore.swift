import Foundation

final class PhotoCaptureStore: @unchecked Sendable {
  struct Capture: Equatable, Sendable {
    let id: String
    let photoHash: String
    let studentId: Int
    let reservationId: Int64
    let createdAt: TimeInterval
    let generation: UInt64
  }
  private let lock = NSLock()
  private let now: () -> TimeInterval
  private var generation: UInt64 = 0
  private var active: Capture?
  private var consumed = false

  init(now: @escaping () -> TimeInterval = { ProcessInfo.processInfo.systemUptime }) { self.now = now }

  func create(_ data: Data, studentId: Int, reservationId: Int64) throws -> Capture {
    guard !data.isEmpty, AppAttestClientData.validStudent(studentId), reservationId > 0 else { throw AttestFailure.rejected }
    let hash = AppAttestClientData.photoHash(data)
    lock.lock(); defer { lock.unlock() }
    generation &+= 1
    let capture = Capture(id: UUID().uuidString.lowercased(), photoHash: hash, studentId: studentId, reservationId: reservationId, createdAt: now(), generation: generation)
    active = capture
    consumed = false
    return capture
  }

  func consume(_ id: String, studentId: Int, reservationId: Int64) throws -> Capture {
    lock.lock(); defer { lock.unlock() }
    guard let capture = active, !consumed, capture.id == id, capture.studentId == studentId,
          capture.reservationId == reservationId, current(capture) else { throw AttestFailure.rejected }
    consumed = true
    return capture
  }

  func isCurrent(_ capture: Capture) -> Bool {
    lock.lock(); defer { lock.unlock() }
    return current(capture)
  }
  private func current(_ capture: Capture) -> Bool {
    let elapsed = now() - capture.createdAt
    return active?.id == capture.id && generation == capture.generation && elapsed >= 0 && elapsed < 120
  }
  func clear() {
    lock.lock(); defer { lock.unlock() }
    generation &+= 1
    active = nil
    consumed = false
  }
  func release(_ id: String) {
    lock.lock(); defer { lock.unlock() }
    if active?.id == id { active = nil; generation &+= 1 }
  }
}
