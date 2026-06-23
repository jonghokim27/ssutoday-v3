package kr.ac.ssu.ssutoday.api.device

import jakarta.validation.Valid
import kr.ac.ssu.ssutoday.api.common.ResponseStatus
import kr.ac.ssu.ssutoday.api.config.LoginStudent
import kr.ac.ssu.ssutoday.api.device.dto.DeviceOptionRequest
import kr.ac.ssu.ssutoday.api.device.dto.DeviceOptionsResponse
import kr.ac.ssu.ssutoday.api.device.dto.DeviceRegisterRequest
import kr.ac.ssu.ssutoday.api.device.dto.DeviceRequest
import kr.ac.ssu.ssutoday.api.device.dto.DeviceVersionRequest
import kr.ac.ssu.ssutoday.application.device.DeviceApplicationService
import kr.ac.ssu.ssutoday.application.device.dto.DeviceKey
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.domain.student.DeviceOption
import kr.ac.ssu.ssutoday.domain.student.StudentView
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/device")
class DeviceController(
    private val deviceApplicationService: DeviceApplicationService,
) {
    @PostMapping("/register")
    @ResponseStatus(StatusCode.SSU2040)
    fun registerDevice(
        @LoginStudent student: StudentView,
        @Valid @RequestBody request: DeviceRegisterRequest,
    ) {
        deviceApplicationService.register(request.key(student.id), request.pushToken)
    }

    @PostMapping("/unregister")
    @ResponseStatus(StatusCode.SSU2050)
    fun unregisterDevice(
        @LoginStudent student: StudentView,
        @Valid @RequestBody request: DeviceRequest,
    ) {
        deviceApplicationService.unregister(request.key(student.id))
    }

    @PostMapping("/checkVersion")
    @ResponseStatus(StatusCode.SSU2070)
    fun checkVersion(
        @Valid @RequestBody request: DeviceVersionRequest,
    ): StatusCode =
        if (deviceApplicationService.isUpdateRequired(request.osType, request.version)) {
            StatusCode.SSU2071
        } else {
            StatusCode.SSU2070
        }

    @PostMapping("/getOption")
    @ResponseStatus(StatusCode.SSU2170)
    fun getOptions(
        @LoginStudent student: StudentView,
        @Valid @RequestBody request: DeviceRequest,
    ): DeviceOptionsResponse {
        val result = deviceApplicationService.getOptions(request.key(student.id))
        return DeviceOptionsResponse(result.notice, result.reserve, result.lms)
    }

    @PostMapping("/updateOption")
    @ResponseStatus(StatusCode.SSU2180)
    fun updateOption(
        @LoginStudent student: StudentView,
        @Valid @RequestBody request: DeviceOptionRequest,
    ) {
        deviceApplicationService.updateOption(
            DeviceKey(student.id, request.osType, request.uuid),
            DeviceOption.valueOf(request.option.uppercase()),
            request.value,
        )
    }

    private fun DeviceRequest.key(studentId: Int) = DeviceKey(studentId, osType, uuid)

    private fun DeviceRegisterRequest.key(studentId: Int) = DeviceKey(studentId, osType, uuid)
}
