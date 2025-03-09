plugins {
    java
}

dependencies {

}

tasks.register<JavaExec>("runServer") {
    group = "application"
    description = "Runs the server application by executing the ServerApp class."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.mulligan.server.ServerApp")

    workingDir = project.rootDir
}