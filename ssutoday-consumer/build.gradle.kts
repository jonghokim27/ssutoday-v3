plugins {
    kotlin("plugin.spring")
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":ssutoday-application"))
    implementation(project(":ssutoday-domain"))
    implementation(project(":ssutoday-common:ssutoday-core"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.google.firebase:firebase-admin:9.2.0")
    runtimeOnly("com.mysql:mysql-connector-j")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
