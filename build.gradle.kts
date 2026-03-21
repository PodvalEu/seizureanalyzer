plugins {
    kotlin("jvm") version "1.9.25"
    application
}

group = "eu.podval.seizureanalyzer"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.api-client:google-api-client:2.6.0")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.36.0")
    implementation("com.google.oauth-client:google-oauth-client-java6:1.36.0")
    implementation("com.google.apis:google-api-services-calendar:v3-rev20240111-2.0.0")

    implementation("com.github.ajalt.clikt:clikt:4.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    implementation("com.github.doyaaaaaken:kotlin-csv-jvm:1.10.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.1")
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")
}

application {
    mainClass.set("seizureanalyzer.MainKt")
}

kotlin {
    jvmToolchain(21)
}
