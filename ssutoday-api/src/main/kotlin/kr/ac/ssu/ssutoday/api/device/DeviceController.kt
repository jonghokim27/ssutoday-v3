package kr.ac.ssu.ssutoday.api.device

import jakarta.validation.Valid
import kr.ac.ssu.ssutoday.api.config.LoginStudent
import kr.ac.ssu.ssutoday.api.common.SsuResponse
import kr.ac.ssu.ssutoday.api.device.dto.DeviceOptionRequest
import kr.ac.ssu.ssutoday.api.device.dto.DeviceOptionsResponse
import kr.ac.ssu.ssutoday.api.device.dto.DeviceRegisterRequest
import kr.ac.ssu.ssutoday.api.device.dto.DeviceRequest
import kr.ac.ssu.ssutoday.api.device.dto.DeviceVersionRequest
import kr.ac.ssu.ssutoday.application.device.DeviceApplicationService
import kr.ac.ssu.ssutoday.application.device.dto.DeviceKey
import kr.ac.ssu.ssutoday.domain.student.DeviceOption
import kr.ac.ssu.ssutoday.domain.student.StudentView
import kr.ac.ssu.ssutoday.core.status.SsuStatus
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
    @SsuResponse(SsuStatus.SSU2040)
    fun register(
        @LoginStudent student: StudentView,
        @Valid @RequestBody request: DeviceRegisterRequest,
    ) {
        deviceApplicationService.register(request.key(student.id), request.pushToken)
    }

    @PostMapping("/unregister")
    @SsuResponse(SsuStatus.SSU2050)
    fun unregister(
        @LoginStudent student: StudentView,
        @Valid @RequestBody request: DeviceRequest,
    ) {
        deviceApplicationService.unregister(request.key(student.id))
    }

    @PostMapping("/checkVersion")
    @SsuResponse(SsuStatus.SSU2070)
    fun version(@Valid @RequestBody request: DeviceVersionRequest): SsuStatus =
        if (deviceApplicationService.isUpdateRequired(request.osType, request.version)) {
            SsuStatus.SSU2071
        } else {
            SsuStatus.SSU2070
        }

    @PostMapping("/getOption")
    @SsuResponse(SsuStatus.SSU2170)
    fun options(
        @LoginStudent student: StudentView,
        @Valid @RequestBody request: DeviceRequest,
    ): DeviceOptionsResponse {
        val result = deviceApplicationService.options(request.key(student.id))
        return DeviceOptionsResponse(result.notice, result.reserve, result.lms)
    }

    @PostMapping("/updateOption")
    @SsuResponse(SsuStatus.SSU2180)
    fun update(
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
