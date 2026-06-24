package kr.ac.ssu.ssutoday.api

import jakarta.annotation.PostConstruct
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories
import org.springframework.scheduling.annotation.EnableScheduling
import java.time.ZoneId
import java.util.TimeZone

@EnableScheduling
@EntityScan("kr.ac.ssu.ssutoday.domain")
@EnableJpaRepositories("kr.ac.ssu.ssutoday.domain")
@EnableRedisRepositories("kr.ac.ssu.ssutoday.domain")
@SpringBootApplication(scanBasePackages = ["kr.ac.ssu.ssutoday"])
class ApiApplication

fun main(args: Array<String>) {
    runApplication<ApiApplication>(*args)
}

@PostConstruct
fun setDefaultTimeZone() {
    TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Seoul")))
}
