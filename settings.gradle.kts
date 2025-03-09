plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.7.0"
}

rootProject.name = "5785-ds-ass3-matan-jamal-oran"
include(":ClientCustomer",":Server", ":Common", ":ClientMo", ":ClientPeo", ":Database",":ParkingRecommender")

