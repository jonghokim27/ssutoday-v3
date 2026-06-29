import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.springframework.boot.gradle.plugin.SpringBootPlugin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.21" apply false
    kotlin("plugin.spring") version "2.2.21" apply false
    kotlin("plugin.jpa") version "2.2.21" apply false
    id("org.springframework.boot") version "4.0.6" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0" apply false
}

allprojects {
    group = "kr.ac.ssu"
    version = "2.2.0-RELEASE"

    repositories {
        mavenCentral()
    }
}

subprojects {
    if (path == ":ssutoday-common") {
        return@subprojects
    }

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    extensions.configure<DependencyManagementExtension> {
        imports {
            mavenBom(SpringBootPlugin.BOM_COORDINATES)
        }
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        add("implementation", "org.jetbrains.kotlin:kotlin-reflect")
        add("implementation", "io.github.oshai:kotlin-logging-jvm:7.0.7")
        add("implementation", "org.springframework.boot:spring-boot-starter-jackson")
        add("testImplementation", "org.jetbrains.kotlin:kotlin-test-junit5")
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    tasks.withType<ProcessResources>().configureEach {
        filteringCharset = "UTF-8"
    }
}
