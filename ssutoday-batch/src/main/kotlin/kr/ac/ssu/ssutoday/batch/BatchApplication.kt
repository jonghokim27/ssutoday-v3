package kr.ac.ssu.ssutoday.batch

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@EntityScan("kr.ac.ssu.ssutoday.domain.article")
@EnableJpaRepositories("kr.ac.ssu.ssutoday.domain.article")
@SpringBootApplication(
    scanBasePackages = [
        "kr.ac.ssu.ssutoday.batch",
        "kr.ac.ssu.ssutoday.application.article",
        "kr.ac.ssu.ssutoday.domain.article",
        "kr.ac.ssu.ssutoday.adapter.kafka",
    ],
)
class BatchApplication

fun main(args: Array<String>) {
    runApplication<BatchApplication>(*args)
}
