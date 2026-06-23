plugins {
    kotlin("plugin.spring")
}

dependencies {
    implementation(project(":ssutoday-common:ssutoday-core"))
    implementation(project(":ssutoday-domain"))
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.cloud:spring-cloud-starter-aws:2.2.6.RELEASE")
    implementation("io.jsonwebtoken:jjwt:0.9.1")
    implementation("javax.xml.bind:jaxb-api:2.4.0-b180830.0359")
    implementation("org.jsoup:jsoup:1.16.1")
    implementation("com.google.firebase:firebase-admin:9.2.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
