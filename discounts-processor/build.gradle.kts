plugins {
    java
    application
    kotlin("jvm") version "1.9.22"
    id("com.diffplug.spotless") version "6.25.0"
    id("io.freefair.lombok") version "8.4"
}

group = "com.example"
version = "1.0-SNAPSHOT"

val javaVersion = 21

kotlin {
    jvmToolchain(javaVersion)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
}

repositories {
    mavenCentral()
}

val kafkaVersion = "3.4.0"
val junitVersion = "5.10.1"
val mockitoVersion = "5.3.1"
val assertjVersion = "3.24.2"
val testcontainersVersion = "1.19.3"
val kotestVersion = "5.8.0"

dependencies {
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    implementation("org.apache.kafka:kafka-streams:$kafkaVersion")
    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.3")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.3")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.12")

    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    testCompileOnly("org.projectlombok:lombok:1.18.30")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.30")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
    testImplementation("org.mockito:mockito-junit-jupiter:$mockitoVersion")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    testImplementation("org.testcontainers:kafka:$testcontainersVersion")
    testImplementation("org.awaitility:awaitility:4.2.0")
    testImplementation("commons-codec:commons-codec:1.15")
    testImplementation("org.apache.commons:commons-lang3:3.12.0")
    testImplementation("org.apache.kafka:kafka-streams-test-utils:$kafkaVersion")

    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-framework-api:$kotestVersion")
    testImplementation("io.kotest:kotest-property:$kotestVersion")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")

    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.14.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}

sourceSets {
    main {
        java {
            srcDirs("src/main/java", "src/main/kotlin")
        }
    }
    test {
        java {
            srcDirs("src/test/java", "src/test/kotlin")
        }
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        showExceptions = true
        showCauses = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"

    useJUnitPlatform {
        includeTags("integration")
    }

    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }

    outputs.upToDateWhen { false }

    systemProperty("junit.jupiter.execution.timeout.default", "5m")
}

application {
    mainClass.set("com.example.DiscountsProcessor")
    applicationDefaultJvmArgs =
        listOf(
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
            "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
        )
}

tasks.jar {
    archiveBaseName.set("discounts-processor")
    archiveVersion.set("1.0-SNAPSHOT")
    manifest {
        attributes(
            "Main-Class" to "com.example.DiscountsProcessor",
        )
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:deprecation")
}

fun removeComments(
    content: String,
    preserveJavaDoc: Boolean = true,
): String {
    var result =
        content
            .replace(Regex("""//.*$""", RegexOption.MULTILINE), "")

    if (!preserveJavaDoc) {
        result = result.replace(Regex("""/\*\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
    }

    return result
        .replace(Regex("""/\*[^*].*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""\n\s*\n\s*\n"""), "\n\n")
}

spotless {
    format("misc") {
        target("*.md", ".gitignore", ".editorconfig")
        trimTrailingWhitespace()
        indentWithSpaces(2)
        endWithNewline()
    }

    java {
        target("src/*/java/**/*.java")
        toggleOffOn()
        importOrder()
        removeUnusedImports()
        googleJavaFormat()
        formatAnnotations()

        licenseHeader("")

        custom("remove comments") { content ->
            removeComments(content, preserveJavaDoc = true)
        }
    }

    kotlin {
        target("src/*/kotlin/**/*.kt")
        ktlint("0.50.0").editorConfigOverride(
            mapOf(
                "ktlint_standard_no-wildcard-imports" to "disabled",
                "ktlint_standard_max-line-length" to "disabled",
                "indent_size" to "2",
            ),
        )
        trimTrailingWhitespace()
        indentWithSpaces(2)
        endWithNewline()

        licenseHeader("")

        custom("remove comments") { content ->
            removeComments(content, preserveJavaDoc = true)
        }
    }

    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

tasks.named("build") {
    dependsOn("spotlessApply")
}

application {
    mainClass.set("com.example.processor.SummarizeViewsKt")
}

tasks.register<JavaExec>("summarizeViews") {
    group = "application"
    description = "Executes SummarizeViews.kt"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.processor.SummarizeViewsKt")

    standardInput = System.`in`

    if (project.hasProperty("args")) {
        args(project.property("args").toString().split("\\s+".toRegex()))
    }
}
