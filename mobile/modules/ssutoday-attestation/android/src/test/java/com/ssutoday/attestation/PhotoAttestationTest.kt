package com.ssutoday.attestation

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PhotoAttestationTest {
  @Test
  fun `canonical bytes match the server vector`() {
    val hash = PhotoClientData.photoHash("abc".toByteArray())
    assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash)
    val result = PhotoClientData.requestHashBytes(20260000, 42, CHALLENGE, hash)
    assertEquals("bF7nJGb7o2lT2ogovdDp0VPtyO_xoHxNgqb3OaAcDaU", Base64.getUrlEncoder().withoutPadding().encodeToString(result))
    assertFalse(result.contentEquals(PhotoClientData.requestHashBytes(20260001, 42, CHALLENGE, hash)))
    assertFalse(result.contentEquals(PhotoClientData.requestHashBytes(20260000, 43, CHALLENGE, hash)))
    assertThrows(IllegalArgumentException::class.java) { PhotoClientData.requestHashBytes(20260000, 42, CHALLENGE.dropLast(1) + "9", hash) }
    assertThrows(IllegalArgumentException::class.java) { PhotoClientData.requestHashBytes(20260000, 42, CHALLENGE, hash.uppercase()) }
  }

  @Test
  fun `capture is immutable single use and expires on monotonic time`() {
    var time = 100L
    val store = PhotoCaptureStore { time }
    val bytes = "camera".toByteArray()
    val capture = store.create(bytes, 20260000, 42)
    bytes.fill(0)
    assertEquals(PhotoClientData.photoHash("camera".toByteArray()), store.consume(capture.id, 20260000, 42).photoHash)
    assertThrows(IllegalStateException::class.java) { store.consume(capture.id, 20260000, 42) }
    assertTrue(store.isCurrent(capture))
    time += 119_999
    assertTrue(store.isCurrent(capture))
    time++
    assertFalse(store.isCurrent(capture))
  }

  @Test
  fun `new capture logout release and navigation invalidate in flight results`() {
    val store = PhotoCaptureStore()
    val first = store.create("first".toByteArray(), 20260000, 42)
    store.consume(first.id, 20260000, 42)
    val second = store.create("second".toByteArray(), 20260000, 42)
    assertFalse(store.isCurrent(first))
    store.release(first.id)
    assertTrue(store.isCurrent(second))
    store.consume(second.id, 20260000, 42)
    store.clear()
    assertFalse(store.isCurrent(second))
    val third = store.create("third".toByteArray(), 20260000, 42)
    store.release(third.id)
    assertThrows(IllegalStateException::class.java) { store.consume(third.id, 20260000, 42) }
  }

  @Test
  fun `capture cannot be claimed for another account or reservation`() {
    val store = PhotoCaptureStore()
    val capture = store.create("camera".toByteArray(), 20260000, 42)
    assertThrows(IllegalStateException::class.java) { store.consume(capture.id, 20260001, 42) }
    assertThrows(IllegalStateException::class.java) { store.consume(capture.id, 20260000, 43) }
    assertEquals(capture, store.consume(capture.id, 20260000, 42))
  }

  @Test
  fun `only one concurrent request can claim a camera capture`() {
    val store = PhotoCaptureStore()
    val capture = store.create("camera".toByteArray(), 20260000, 42)
    val executor = Executors.newFixedThreadPool(8)
    val start = CountDownLatch(1)
    try {
      val results = (1..8).map {
        executor.submit<Boolean> {
          start.await()
          try { store.consume(capture.id, 20260000, 42); true } catch (_: IllegalStateException) { false }
        }
      }
      start.countDown()
      assertEquals(1, results.count { it.get(5, TimeUnit.SECONDS) })
    } finally {
      executor.shutdownNow()
    }
  }

  private companion object {
    const val CHALLENGE = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"
  }
}
