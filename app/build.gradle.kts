plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

repositories {
    mavenCentral()
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/VirtusLab/vived-engine")
        credentials {
            username = project.findProperty("githubUsername") as String?
                ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("githubToken") as String?
                ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("dev.vived:vived-engine:0.1.0-SNAPSHOT")
    implementation(libs.jobrunr)
    implementation(libs.h2)
    implementation(libs.kaml)

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.duckdb.jdbc)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "jvm.daily.AppKt"
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("explore") {
    description = "Run interactive DuckDB explorer"
    group = "application"
    mainClass.set("jvm.daily.ExploreDbKt")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.projectDir
    standardInput = System.`in`
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

tasks.register<Test>("integrationTest") {
    description = "Run integration tests (require network)"
    group = "verification"
    useJUnitPlatform {
        includeTags("integration")
    }
}
