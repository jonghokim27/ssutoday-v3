plugins {
    kotlin("plugin.spring")
}

dependencies {
    implementation(project(":ssutoday-domain"))
    implementation(project(":ssutoday-common:ssutoday-core"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework:spring-tx")
    implementation("org.springframework.data:spring-data-commons")
}
