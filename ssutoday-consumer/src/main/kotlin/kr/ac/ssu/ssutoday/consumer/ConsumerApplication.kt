package kr.ac.ssu.ssutoday.consumer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories

@EntityScan("kr.ac.ssu.ssutoday.domain")
@EnableJpaRepositories("kr.ac.ssu.ssutoday.domain")
@EnableRedisRepositories("kr.ac.ssu.ssutoday.domain")
@SpringBootApplication(scanBasePackages = ["kr.ac.ssu.ssutoday"])
class ConsumerApplication

fun main(args: Array<String>) {
    runApplication<ConsumerApplication>(*args)
}
