plugins {
    id("java")
    application
}

group = "com.godrickk"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_18
    targetCompatibility = JavaVersion.VERSION_18
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

application {
    mainClass.set("com.godrickk.entities.PhoneGrouper")
}

tasks.register<Jar>("createJar") {
    archiveFileName.set("phone-grouper.jar")
    destinationDirectory.set(project.rootDir)

    manifest {
        attributes(
            mapOf(
                "Main-Class" to "com.godrickk.entities.PhoneGrouper"
            )
        )
    }

    from(sourceSets.main.get().output)

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.build {
    dependsOn("createJar")
}

tasks.test {
    useJUnitPlatform()
}