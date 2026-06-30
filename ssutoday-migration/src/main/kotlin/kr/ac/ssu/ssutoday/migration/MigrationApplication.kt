package kr.ac.ssu.ssutoday.migration

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.time.ZoneId
import java.util.TimeZone

@SpringBootApplication
class MigrationApplication

fun main(args: Array<String>) {
    TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Seoul")))
    runApplication<MigrationApplication>(*args)
}
