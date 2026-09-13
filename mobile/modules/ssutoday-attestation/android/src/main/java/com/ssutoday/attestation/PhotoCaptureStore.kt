package com.ssutoday.attestation

import java.util.UUID

/** 앱 내부 카메라의 최종 JPEG만 등록한다. 웹에는 파일 URI/해시를 등록하는 메서드를 제공하지 않는다. */
internal class PhotoCaptureStore(
  private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) {
  data class Capture(val id: String, val photoHash: String, val studentId: Int, val reservationId: Long, val generation: Long, val createdAt: Long)

  private var generation = 0L
  private var activeId: String? = null
  private var capture: Capture? = null

  @Synchronized
  fun create(bytes: ByteArray, studentId: Int, reservationId: Long): Capture {
    require(bytes.isNotEmpty() && studentId > 0 && reservationId > 0)
    clear()
    val next = Capture(UUID.randomUUID().toString(), PhotoClientData.photoHash(bytes), studentId, reservationId, generation, nowMillis())
    capture = next
    activeId = next.id
    return next
  }

  @Synchronized
  fun consume(id: String, studentId: Int, reservationId: Long): Capture {
    val current = capture
    check(current != null && current.id == id && current.studentId == studentId && current.reservationId == reservationId && isCurrent(current)) {
      "Capture is unavailable"
    }
    capture = null
    return current
  }

  @Synchronized
  fun isCurrent(value: Capture): Boolean =
    generation == value.generation && activeId == value.id && nowMillis() - value.createdAt in 0 until TTL_MILLIS

  @Synchronized
  fun release(id: String) {
    if (activeId == id) clear()
  }

  @Synchronized
  fun clear() {
    generation++
    activeId = null
    capture = null
  }

  private companion object {
    const val TTL_MILLIS = 120_000L
  }
}
