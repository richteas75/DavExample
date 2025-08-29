plugins {
    java
    id("org.jetbrains.kotlin.jvm") version "1.9.23" // The dav4jvm library is built with Kotlin, so you'll need the Kotlin plugin.
}

group = "name.andreasrichter"
version = "1.0-SNAPSHOT"


repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") } //for dav4jvm
}

dependencies {
    implementation("com.github.bitfireAT:dav4jvm:2.2.1") // The dav4jvm library
    implementation("org.slf4j:slf4j-simple:2.0.12") // A simple logger is needed for the library to work properly.
}

tasks.jar {
    //to handle duplicate entries
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    manifest {
        attributes["Main-Class"] = "name.andreasrichter.DavExample.Dav4jvmExample"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

