package kr.ac.ssu.ssutoday.domain.student

data class DeviceAttestationView(
    val keyId: String,
    val studentId: Int,
    val publicKey: ByteArray,
    val production: Boolean,
    val registrationHash: ByteArray,
    val counter: Long,
)
