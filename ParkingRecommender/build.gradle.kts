plugins {
    java
}

dependencies{
    testImplementation ("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly ("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testImplementation ("org.mockito:mockito-core:4.8.0")
    testImplementation ("org.mockito:mockito-junit-jupiter:4.8.0")
}

tasks.test {
    useJUnitPlatform()
}







