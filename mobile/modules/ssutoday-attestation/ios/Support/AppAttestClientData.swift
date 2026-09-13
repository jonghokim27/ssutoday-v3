import Foundation
#if canImport(CryptoKit)
import CryptoKit
#else
import Crypto
#endif

enum AppAttestClientData {
  static func hash(_ data: Data) -> Data { Data(SHA256.hash(data: data)) }
  static func photoHash(_ data: Data) -> String { hash(data).map { String(format: "%02x", $0) }.joined() }
  static func validStudent(_ studentId: Int) -> Bool { studentId > 0 && studentId <= Int(Int32.max) }
  static func validChallenge(_ value: String) -> Bool {
    value.range(of: "^[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]$", options: .regularExpression) != nil
  }
  static func validKeyId(_ value: String) -> Bool {
    guard value.count == 44, let decoded = Data(base64Encoded: value) else { return false }
    return decoded.count == 32 && decoded.base64EncodedString() == value
  }
  static func registration(studentId: Int, challenge: String, keyId: String) throws -> Data {
    guard validStudent(studentId), validChallenge(challenge), validKeyId(keyId) else { throw AttestFailure.rejected }
    return Data("ssutoday.attestation.v1\npurpose=APP_ATTEST_REGISTER\nstudentId=\(studentId)\nchallenge=\(challenge)\nkeyId=\(keyId)\n".utf8)
  }
  static func photo(studentId: Int, reservationId: Int64, challenge: String, photoHash: String) throws -> Data {
    guard validStudent(studentId), reservationId > 0, reservationId <= 9_007_199_254_740_991,
          validChallenge(challenge), photoHash.range(of: "^[0-9a-f]{64}$", options: .regularExpression) != nil else { throw AttestFailure.rejected }
    return Data("ssutoday.attestation.v1\npurpose=VERIFY_PHOTO_UPLOAD\nstudentId=\(studentId)\nreservationId=\(reservationId)\nchallenge=\(challenge)\nphotoSha256=\(photoHash)\n".utf8)
  }

  static func reservation(studentId: Int, roomNo: String, date: String, startBlock: Int, endBlock: Int, challenge: String) throws -> Data {
    guard validStudent(studentId), !roomNo.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, roomNo.utf16.count <= 100,
          (12...43).contains(startBlock), (startBlock...43).contains(endBlock), validChallenge(challenge),
          date.range(of: "^202[3-9]-[0-9]{2}-[0-9]{2}$", options: .regularExpression) != nil else { throw AttestFailure.rejected }
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    formatter.dateFormat = "yyyy-MM-dd"
    formatter.isLenient = false
    guard let parsed = formatter.date(from: date), formatter.string(from: parsed) == date else { throw AttestFailure.rejected }
    let room = Data(roomNo.utf8).map { String(format: "%02x", $0) }.joined()
    return Data("ssutoday.attestation.v1\npurpose=RESERVATION_CREATE\nstudentId=\(studentId)\nroomNoUtf8Hex=\(room)\ndate=\(date)\nstartBlock=\(startBlock)\nendBlock=\(endBlock)\nchallenge=\(challenge)\n".utf8)
  }
}
