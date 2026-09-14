package com.ssutoday.attestation

import android.net.Uri
import android.util.Base64
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityException
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.functions.Coroutine
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SsutodayAttestationModule : Module() {
  private val captures = PhotoCaptureStore()
  private val manager by lazy { IntegrityManagerFactory.createStandard(requireNotNull(appContext.reactContext)) }
  private var preparation: Task<StandardIntegrityTokenProvider>? = null

  override fun definition() = ModuleDefinition {
    Name("SsutodayAttestation")

    // RN 환경에서만 사용한다. 메인 프레임 메시지 인증에 쓰며 웹 요청 파라미터로 설정할 수 없다.
    Function("createBridgeToken") { UUID.randomUUID().toString() + UUID.randomUUID().toString() }
    Function("clearCaptures") { captures.clear() }
    Function("releaseCapture") { id: String -> captures.release(id) }

    AsyncFunction("prepare") Coroutine { ->
      try {
        withTimeout(25_000L) { prepareProvider().awaitResult() }
      } catch (error: Exception) {
        throw integrityError(error)
      }
      Unit
    }

    // uri는 RN이 앱 내부 카메라 촬영 후 압축한 결과로만 전달한다. WebView bridge에는 노출하지 않는다.
    AsyncFunction("storeCapture") { uri: String, studentId: Int, reservationId: Long ->
      val context = requireNotNull(appContext.reactContext)
      val parsed = Uri.parse(uri)
      if (parsed.scheme != "file") throw captureRejected()
      val file = File(requireNotNull(parsed.path)).canonicalFile
      val cacheRoot = context.cacheDir.canonicalFile.path + File.separator
      if (!file.path.startsWith(cacheRoot) || !file.isFile || file.length() !in 3L..MAX_PHOTO_BYTES) throw captureRejected()
      val bytes = file.inputStream().use { it.readBytes() }
      if (bytes.size !in 3..MAX_PHOTO_BYTES || bytes[0] != 0xff.toByte() || bytes[1] != 0xd8.toByte()) throw captureRejected()
      val capture = captures.create(bytes, studentId, reservationId)
      mapOf(
        "captureId" to capture.id,
        "photoSha256" to capture.photoHash,
        "uri" to "data:image/jpeg;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}",
      )
    }

    AsyncFunction("attest") Coroutine { captureId: String, studentId: Int, reservationId: Long, challenge: String ->
      val capture = try { captures.consume(captureId, studentId, reservationId) } catch (_: IllegalStateException) { throw captureRejected() }
      val requestHash = try {
        Base64.encodeToString(
          PhotoClientData.requestHashBytes(studentId, reservationId, challenge, capture.photoHash),
          Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
      } catch (_: IllegalArgumentException) { throw captureRejected() }
      val token = integrityToken(requestHash)
      if (!captures.isCurrent(capture)) throw captureRejected()
      mapOf("platform" to "android", "attestation" to token)
    }

    AsyncFunction("attestReservation") Coroutine { studentId: Int, roomNo: String, date: String, startBlock: Int, endBlock: Int, challenge: String ->
      val hash = try {
        Base64.encodeToString(PhotoClientData.reservationHashBytes(studentId, roomNo, date, startBlock, endBlock, challenge), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
      } catch (_: Exception) { throw captureRejected() }
      mapOf("platform" to "android", "attestation" to integrityToken(hash))
    }

    OnDestroy { captures.clear() }
  }

  private suspend fun integrityToken(hash: String): String = try {
    withTimeout(25_000L) {
      try { requestToken(hash) }
      catch (error: StandardIntegrityException) {
        if (error.errorCode != StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID) throw error
        synchronized(this@SsutodayAttestationModule) { preparation = null }
        requestToken(hash)
      }
    }
  } catch (_: TimeoutCancellationException) { throw integrityUnavailable() }
    catch (error: StandardIntegrityException) { throw integrityError(error) }

  private fun integrityError(error: Exception): CodedException {
    if (error is StandardIntegrityException && error.errorCode in setOf(
        StandardIntegrityErrorCode.API_NOT_AVAILABLE,
        StandardIntegrityErrorCode.PLAY_STORE_NOT_FOUND,
        StandardIntegrityErrorCode.PLAY_SERVICES_NOT_FOUND,
        StandardIntegrityErrorCode.PLAY_STORE_VERSION_OUTDATED,
        StandardIntegrityErrorCode.PLAY_SERVICES_VERSION_OUTDATED,
      )) return CodedException("ERR_ATTESTATION_UNSUPPORTED", "Attestation is not supported on this device", null)
    return integrityUnavailable()
  }

  @Synchronized
  private fun prepareProvider(): Task<StandardIntegrityTokenProvider> {
    preparation?.let { return it }
    val task = manager.prepareIntegrityToken(
      StandardIntegrityManager.PrepareIntegrityTokenRequest.builder().setCloudProjectNumber(CLOUD_PROJECT_NUMBER).build(),
    )
    preparation = task
    task.addOnFailureListener {
      synchronized(this) { if (preparation === task) preparation = null }
    }
    return task
  }

  private suspend fun requestToken(hash: String): String = prepareProvider().awaitResult().request(
    StandardIntegrityManager.StandardIntegrityTokenRequest.builder().setRequestHash(hash).build(),
  ).awaitResult().token()

  private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
  }

  private fun captureRejected() = CodedException("ERR_CAPTURE_REJECTED", "사진을 다시 촬영해 주세요", null)
  private fun integrityUnavailable() = CodedException("ERR_INTEGRITY_UNAVAILABLE", "앱 무결성 증명을 생성하지 못했습니다", null)

  private companion object {
    const val CLOUD_PROJECT_NUMBER = 997341830534L
    const val MAX_PHOTO_BYTES = 10 * 1024 * 1024
  }
}
