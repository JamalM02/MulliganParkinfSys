plugins {
    java
    application
}

dependencies {

}

application {
    mainClass.set("com.mulligan.customer.CustomerApp")
}

tasks.withType<JavaExec> {
    jvmArgs = listOf(
        "--module-path", configurations.runtimeClasspath.get().asPath,
        "--add-modules", "javafx.controls,javafx.fxml"
    )
}
