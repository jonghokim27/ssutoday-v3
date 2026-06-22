package kr.ac.ssu.ssutoday.domain.student

import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.status.SsuStatus
import org.springframework.stereotype.Service

@Service
class DeviceService(
    private val devices: DeviceRepository,
    private val versions: VersionRepository,
) {
    fun register(studentId: Int, osType: String, uuid: String, pushToken: String) {
        val device = devices.findByStudentIdAndOsTypeAndUuid(studentId, osType, uuid)
            ?.apply { updatePushToken(pushToken) }
            ?: Device(studentId = studentId, osType = osType, uuid = uuid, pushToken = pushToken)
        devices.save(device)
    }

    fun unregister(studentId: Int, osType: String, uuid: String) {
        devices.delete(getDevice(studentId, osType, uuid, SsuStatus.SSU4050))
    }

    fun getOptions(studentId: Int, osType: String, uuid: String): DeviceOptionsView =
        getDevice(studentId, osType, uuid, SsuStatus.SSU4170).let {
            DeviceOptionsView(it.notice == 1, it.reserve == 1, it.lms == 1)
        }

    fun updateOption(studentId: Int, osType: String, uuid: String, option: DeviceOption, enabled: Boolean) {
        getDevice(studentId, osType, uuid, SsuStatus.SSU4180).change(option, enabled)
    }

    fun getRequiredVersion(osType: String): String = versions.getByOsType(osType).requiredVersion

    fun findReservationPushTokens(studentId: Int): List<String> =
        devices.findAllByStudentId(studentId)
            .filter { it.reserve == 1 }
            .map(Device::pushToken)

    private fun getDevice(
        studentId: Int,
        osType: String,
        uuid: String,
        notFoundStatus: SsuStatus,
    ): Device =
        devices.findByStudentIdAndOsTypeAndUuid(studentId, osType, uuid)
            ?: throw BusinessException(notFoundStatus)
}
