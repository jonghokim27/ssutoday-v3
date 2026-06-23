plugins {
    kotlin("plugin.spring")
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":ssutoday-application"))
    implementation(project(":ssutoday-domain"))
    implementation(project(":ssutoday-common:ssutoday-core"))
    runtimeOnly(project(":ssutoday-common:ssutoday-adapter"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.jsoup:jsoup:1.16.1")
    runtimeOnly("com.mysql:mysql-connector-j")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
