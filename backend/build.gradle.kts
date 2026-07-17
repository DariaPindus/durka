plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.spring") version "2.4.0"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.durka"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    // tdlight-java is not published to Maven Central
    maven { url = uri("https://mvn.mchv.eu/repository/mchv/") }
}

// tdlight-java version string as published (contains a literal '+', not a Gradle dynamic-version wildcard)
val tdlightVersion = "3.5.3+td.1.8.65"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // Spring Boot 4 modularized autoconfiguration: FlywayAutoConfiguration now lives in this
    // starter, not in spring-boot-autoconfigure - flyway-core alone (pre-Boot-4 habit) is
    // present on the classpath but is never triggered without it (verified by the complete
    // absence of any Flyway entry, positive or negative, in the --debug condition report).
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    implementation(platform("it.tdlight:tdlight-java-bom:$tdlightVersion"))
    implementation("it.tdlight:tdlight-java")
    // tdlight-natives is versioned independently of tdlight-java (verified against the repo) and is
    // published as ONE artifact with platform classifiers, not per-platform artifact names - version
    // is left to the BOM's dependency management above.
    // Both architectures: dev host is Apple Silicon (Docker runs linux/arm64 natively),
    // a future deployment host may be linux/amd64. OpenSSL 3.x confirmed in the JDK base image -> ssl3 classifier.
    implementation("it.tdlight:tdlight-natives") { artifact { classifier = "linux_amd64_gnu_ssl3" } }
    implementation("it.tdlight:tdlight-natives") { artifact { classifier = "linux_arm64_gnu_ssl3" } }

    // RSS/Atom feed parsing - verified current version directly against Maven Central's search
    // API (its own maven-metadata.xml <latest> tag can be stale, same gotcha as tdlight-java).
    implementation("com.rometools:rome:2.1.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    // BootRun extends JavaExec, which does NOT forward the real terminal's stdin to the
    // forked JVM by default - without this, the auth-cli profile's interactive phone/code/
    // 2FA prompts print fine (stdout) but never receive what you type (stdin goes nowhere),
    // so it just hangs forever looking like a stuck progress bar.
    standardInput = System.`in`
}
