import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask

plugins {
    id("java")
    id("distribution")
    id("jacoco")
    id("checkstyle")
    id("com.github.spotbugs") version "6.0.26"
}

group = "io.template"
version = "1.0-SNAPSHOT"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}

jacoco {
    toolVersion = "0.8.14"
}

checkstyle {
    toolVersion = "10.17.0"
    configFile = file("checkstyle-configuration.xml")
    isIgnoreFailures = false
}

spotbugs {
    toolVersion.set("4.9.8")
    ignoreFailures.set(false)
    effort.set(Effort.MAX)
    reportLevel.set(Confidence.LOW)
    excludeFilter.set(file("spotbugs-configuration.xml"))
}

repositories {
    mavenCentral()
}

dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.STRICT
}

dependencies {
    constraints {
        // Guice pulls in an older Guava that uses sun.misc.Unsafe (terminally deprecated in JDK 25). Force a newer version that migrated to VarHandle, eliminating the warning.
        implementation("com.google.guava:guava:33.4.8-jre")
    }

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("ch.qos.logback:logback-classic:1.5.21")

    // Dependency Injection
    implementation("com.google.inject:guice:7.0.0")

    // JSON `serde`
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.20.1")

    // AWS SDK
    implementation(platform("software.amazon.awssdk:bom:2.55.11"))
    implementation("software.amazon.awssdk:regions")
    implementation("software.amazon.awssdk:dynamodb")

    // Validators
    implementation(platform("org.hibernate.validator:hibernate-validator-bom:9.1.0.Final"))
    implementation("org.hibernate.validator:hibernate-validator")
    implementation("org.glassfish.expressly:expressly:6.0.0")

    // Spotbugs
    testCompileOnly("com.github.spotbugs:spotbugs-annotations:4.9.8")

    // JUnit
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Mockito
    testImplementation("org.mockito:mockito-core:5.21.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.21.0")
}

/**
 * Gradle Clean Task Configurations
 */

tasks.named<Delete>("clean") {
    delete("bin")
}

/**
 * Include the application JAR and runtime dependency JARs in the standard build.
 */

distributions {
    main {
        contents {
            into("lib") {
                from(tasks.named("jar"))
                from(configurations.runtimeClasspath)
            }
        }
    }
}

tasks.named<Sync>("installDist") {
    into(layout.buildDirectory.dir("release/executable"))
}

tasks.register<Tar>("packageRelease") {
    archiveFileName.set("release-artifact.tar.gz")
    destinationDirectory.set(layout.buildDirectory)
    compression = Compression.GZIP
    from("Dockerfile", "flake.nix", "flake.lock", ".dockerignore")
    from(tasks.named("installDist")) {
        into("executable")
    }
}

tasks.named("build") {
    dependsOn(tasks.named("packageRelease"))
}

/**
 * Gradle Compile Task Configurations
 */

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Enable all javac lint warnings (unused variables, fallthrough, etc.) and treat them as errors
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

/**
 * Gradle Javadoc Task Configurations
 */

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        memberLevel = JavadocMemberLevel.PUBLIC
        addBooleanOption("Xdoclint:all,-missing", true)
    }
}

/**
 * Gradle Test Task Configurations
 */

tasks.test {
    // Jacoco appends to the bootstrap classpath for instrumentation, which prevents CDS (Class Data
    // Sharing) from working and produces a noisy warning. Disable CDS here since it is a startup
    // optimization for long-lived processes and irrelevant for a short-lived test JVM.
    jvmArgs("-Xshare:off")
    useJUnitPlatform()
    reports { junitXml.required = true; html.required = true }
    testLogging {
        events("skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showCauses = true
        showStackTraces = true
    }
}

/**
 * Gradle Check Task Configurations
 */

tasks.check {
    dependsOn(
        tasks.jacocoTestCoverageVerification,
        tasks.checkstyleMain,
        tasks.checkstyleTest,
        tasks.spotbugsMain,
        tasks.spotbugsTest,
        tasks.javadoc
    )
}

/**
 * Jacaco Configurations
 */

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)

    val excludes = listOf(
        "io/template/Main.class",
        "io/template/composition/*"
    )
    classDirectories.setFrom(
        sourceSets.main.get().output.asFileTree.matching {
            exclude(excludes)
        }
    )

    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

/**
 * Checkstyle Configurations
 */

tasks.withType<Checkstyle>().configureEach {
    reports {
        xml.required.set(false)
        html.required.set(true)
    }
}

/**
 * Spotbugs Configurations
 */

tasks.withType<SpotBugsTask>().configureEach {
    reports.maybeCreate("xml").required.set(true)
    reports.maybeCreate("html").required.set(true)
}

tasks.spotbugsMain {
    dependsOn(tasks.classes)
}

tasks.spotbugsTest {
    dependsOn(tasks.testClasses)
}
