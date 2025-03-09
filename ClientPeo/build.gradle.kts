plugins {
    java
    application
}

dependencies {

}

application {
    mainClass.set("com.mulligan.peo.ClientPeoMain")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "com.mulligan.peo.ClientPeoMain"
    }
}
