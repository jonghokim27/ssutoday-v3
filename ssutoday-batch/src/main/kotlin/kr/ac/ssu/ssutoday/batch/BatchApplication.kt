package kr.ac.ssu.ssutoday.batch

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@EntityScan("kr.ac.ssu.ssutoday.domain")
@EnableJpaRepositories("kr.ac.ssu.ssutoday.domain")
@SpringBootApplication(scanBasePackages = ["kr.ac.ssu.ssutoday"])
class BatchApplication

fun main(args: Array<String>) {
    runApplication<BatchApplication>(*args)
}
