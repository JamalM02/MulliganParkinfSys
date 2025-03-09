import org.gradle.internal.os.OperatingSystem

plugins {
    java
    id("project-report")
    id("jacoco")
}

group = "com.mulligan"
version = "1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(19))
    }
}

jacoco {
    toolVersion = "0.8.8"
}

allprojects {
    repositories {
        mavenCentral()
    }
}

// Query the current operating system
val currentOS = OperatingSystem.current()
val platform = when {
    currentOS.isWindows -> "win"
    currentOS.isLinux -> "linux"
    currentOS.isMacOsX -> "mac"
    else -> throw GradleException("Unsupported OS: $currentOS")
}

subprojects {
    apply(plugin = "java")

    dependencies {
        // Common dependencies for all subprojects
        implementation("org.openjfx:javafx-controls:19.0.2:$platform")
        implementation("org.openjfx:javafx-fxml:19.0.2:$platform")
        implementation("org.openjfx:javafx-graphics:19.0.2:$platform")
        implementation("org.openjfx:javafx-base:19.0.2:$platform")
        implementation("com.rabbitmq:amqp-client:5.15.0")
        implementation("org.json:json:20210307")
        implementation("ch.qos.logback:logback-classic:1.4.11")
        implementation("org.slf4j:slf4j-api:2.0.9")
        implementation ("org.mongodb:mongodb-driver-sync:4.10.2")
        implementation ("io.github.cdimascio:dotenv-java:3.1.0")
        implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")


        configurations.all {
            exclude(group = "org.slf4j", module = "slf4j-simple")
        }

        testImplementation ("junit:junit:4.13.2")
        testImplementation(platform("org.junit:junit-bom:5.10.0"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testImplementation("org.mockito:mockito-core:5.5.0")
        testImplementation("org.mockito:mockito-junit-jupiter:5.5.0")
        testImplementation("org.testfx:testfx-core:4.0.16-alpha")
        testImplementation("org.testfx:testfx-junit5:4.0.16-alpha")
    }


    // Conditional dependencies for specific modules
    if (name != "Common" && name != "Database" ) {
        dependencies {
            implementation(project(":Common"))   // Use shared Common module
            implementation(project(":Database")) // Use shared Database module

        }
    }
    if (name == "Server") {
        dependencies {
            implementation(project(":ParkingRecommender"))         // Use shared Server module
        }
    }
    tasks.withType<JavaExec> {
        jvmArgs(
            "--module-path", configurations.runtimeClasspath.get().asPath,
            "--add-modules", "javafx.controls,javafx.fxml"
        )
        workingDir = project.rootDir
    }

    // Configure tests to use JavaFX and generate Jacoco reports
    tasks.test {
        useJUnitPlatform()

        jvmArgs(
            "--module-path", configurations.runtimeClasspath.get().asPath,
            "--add-modules", "javafx.controls,javafx.fxml"
        )

        finalizedBy("jacocoTestReport") // Run Jacoco after tests
    }

    // Configure Jacoco test report
    tasks.register<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.test)
        reports {
            xml.required.set(true)
            csv.required.set(false)
            html.outputLocation.set(layout.buildDirectory.dir("jacocoHtml"))
        }
        sourceDirectories.setFrom(files(subprojects.flatMap { it.sourceSets.main.get().allSource.srcDirs }))
        classDirectories.setFrom(files(subprojects.flatMap { it.sourceSets.main.get().output }))
        executionData.setFrom(files(subprojects.flatMap { it.tasks.withType<Test>().map { testTask -> testTask.extensions.getByType<JacocoTaskExtension>().destinationFile }}))
    }
}
