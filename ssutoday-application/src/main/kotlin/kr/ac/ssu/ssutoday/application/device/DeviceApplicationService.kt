package kr.ac.ssu.ssutoday.application.device

import kr.ac.ssu.ssutoday.application.device.dto.DeviceKey
import kr.ac.ssu.ssutoday.application.device.dto.DeviceOptions
import kr.ac.ssu.ssutoday.domain.student.DeviceOption
import kr.ac.ssu.ssutoday.domain.student.DeviceService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeviceApplicationService(
    private val deviceService: DeviceService,
) {
    @Transactional
    fun register(key: DeviceKey, pushToken: String) {
        deviceService.register(key.studentId, key.osType, key.uuid, pushToken)
    }

    @Transactional
    fun unregister(key: DeviceKey) {
        deviceService.unregister(key.studentId, key.osType, key.uuid)
    }

    @Transactional(readOnly = true)
    fun options(key: DeviceKey): DeviceOptions = deviceService.getOptions(key.studentId, key.osType, key.uuid).let {
        DeviceOptions(it.notice, it.reserve, it.lms)
    }

    @Transactional
    fun updateOption(key: DeviceKey, option: DeviceOption, enabled: Boolean) {
        deviceService.updateOption(key.studentId, key.osType, key.uuid, option, enabled)
    }

    @Transactional(readOnly = true)
    fun isUpdateRequired(osType: String, current: String): Boolean =
        SemanticVersion(current) < SemanticVersion(deviceService.getRequiredVersion(osType))
}
