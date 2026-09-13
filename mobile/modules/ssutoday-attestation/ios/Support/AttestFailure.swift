import Foundation

enum AttestFailure: Error, Equatable {
  case unavailable
  case invalidKey
  case rejected
  case busy
}
