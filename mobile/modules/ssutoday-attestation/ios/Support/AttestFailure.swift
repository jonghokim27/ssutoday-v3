import Foundation

enum AttestFailure: Error, Equatable {
  case unsupported
  case unavailable
  case invalidKey
  case rejected
  case busy
}
