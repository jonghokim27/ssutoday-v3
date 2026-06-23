package kr.ac.ssu.ssutoday.core.config

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import java.time.ZoneId
import java.util.TimeZone

@Configuration
class TimeConfig(
    @Value("\${ssutoday.time-zone}")
    private val timeZone: String,
) {
    @PostConstruct
    fun setDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(timeZone)))
    }
}
