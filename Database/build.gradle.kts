plugins {
    java

}

group = "com.mulligan"
version = "1.0"

tasks.register<JavaExec>("initDatabase") {
    group = "database"
    description = "Initializes the database by running the DBConnector class."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.mulligan.database.Main")

    // Set working directory to the project root
    workingDir = project.rootDir
    // Pass the database path as a system property
    systemProperty("db.path", "${project.rootDir}/Parking.sqlite")
}

