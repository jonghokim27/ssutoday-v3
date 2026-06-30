package kr.ac.ssu.ssutoday.application.device

import kr.ac.ssu.ssutoday.application.device.dto.DeviceKey
import kr.ac.ssu.ssutoday.application.device.dto.DeviceOptions
import kr.ac.ssu.ssutoday.core.port.PushTopicManager
import kr.ac.ssu.ssutoday.domain.student.DeviceOption
import kr.ac.ssu.ssutoday.domain.student.DeviceService
import kr.ac.ssu.ssutoday.domain.student.StudentService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeviceApplicationService(
    private val deviceService: DeviceService,
    private val studentService: StudentService,
    private val pushTopicManager: PushTopicManager,
) {
    @Transactional
    fun register(
        key: DeviceKey,
        pushToken: String,
    ) {
        val device = deviceService.register(key.studentId, key.osType, key.uuid, pushToken)
        if (device.notice == 1) {
            val major = studentService.get(key.studentId).major
            pushTopicManager.subscribe(pushToken, listOf("all", major))
        }
    }

    @Transactional
    fun unregister(key: DeviceKey) {
        val device = deviceService.unregister(key.studentId, key.osType, key.uuid)
        if (device.notice == 1) {
            val major = studentService.get(key.studentId).major
            pushTopicManager.unsubscribe(device.pushToken, listOf("all", major))
        }
    }

    @Transactional(readOnly = true)
    fun getOptions(key: DeviceKey): DeviceOptions =
        deviceService.getOptions(key.studentId, key.osType, key.uuid).let {
            DeviceOptions(it.notice, it.reserve, it.lms)
        }

    @Transactional
    fun updateOption(
        key: DeviceKey,
        option: DeviceOption,
        enabled: Boolean,
    ) {
        val device = deviceService.updateOption(key.studentId, key.osType, key.uuid, option, enabled)
        if (option == DeviceOption.NOTICE) {
            val major = studentService.get(key.studentId).major
            if (enabled) {
                pushTopicManager.subscribe(device.pushToken, listOf("all", major))
            } else {
                pushTopicManager.unsubscribe(device.pushToken, listOf("all", major))
            }
        }
    }

    @Transactional(readOnly = true)
    fun isUpdateRequired(
        osType: String,
        current: String,
    ): Boolean = SemanticVersion(current) < SemanticVersion(deviceService.getRequiredVersion(osType))
}
