# vived-engine GitHub Packages Publishing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decouple `vived-engine` from `jvm-daily` by publishing it to GitHub Packages and consuming it as an external Maven dependency.

**Architecture:** Add `maven-publish` to `~/Priv/vived-engine`, publish on push to main via GitHub Actions, then update `jvm-daily` to replace the local subproject with the external package. Cross-org access (VirtusLab → ArturSkowronski) requires a PAT stored as a repository secret.

**Tech Stack:** Kotlin/Gradle (Kotlin DSL), GitHub Packages (Maven), GitHub Actions

---

## File Map

**`~/Priv/vived-engine` (VirtusLab/vived-engine repo):**
- Modify: `build.gradle.kts` — add `maven-publish` plugin + `publishing` block
- Create: `.github/workflows/publish.yml` — publish to GH Packages on push to main

**`jvm-daily` (this repo / ArturSkowronski/jvm-daily):**
- Modify: `settings.gradle.kts` — remove `include("vived-engine")`
- Delete: `vived-engine/` — entire directory
- Modify: `app/build.gradle.kts` — replace `project(":vived-engine")` with external dep + add GH Packages repo
- Modify: `.github/workflows/ci.yml` — add `GH_PACKAGES_TOKEN` env to build job
- Manual: `~/.gradle/gradle.properties` — local dev credentials (not committed)

---

## Task 1: Add maven-publish to vived-engine

**Files:**
- Modify: `~/Priv/vived-engine/build.gradle.kts`

> All steps in this task run from `~/Priv/vived-engine`.

- [ ] **Step 1: Open build.gradle.kts and confirm current content**

```bash
cat ~/Priv/vived-engine/build.gradle.kts
```

Expected: file starts with `plugins {`, contains `alias(libs.plugins.kotlin.jvm)`, has `group = "dev.vived"` and `version = "0.1.0"`.

- [ ] **Step 2: Replace build.gradle.kts with publish support added**

Write the full file (preserving all existing content, adding only the publish block):

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `java-library`
    `maven-publish`
}

group = "dev.vived"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.datetime)
    api(libs.kotlinx.serialization.core)

    implementation(libs.koog.agents)
    implementation(libs.duckdb.jdbc)
    implementation(libs.rome)
    implementation(libs.kaml)
    implementation(libs.guava)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
    repositories {
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
}
```

Note: version changed from `0.1.0` to `0.1.0-SNAPSHOT` so it resolves as a snapshot.

- [ ] **Step 3: Verify Gradle can parse the file**

```bash
cd ~/Priv/vived-engine && ./gradlew tasks --group publishing
```

Expected output includes:
```
Publishing tasks
----------------
publish - Publishes all publications produced by this project.
publishMavenPublicationToGitHubPackagesRepository - ...
```

- [ ] **Step 4: Commit**

```bash
cd ~/Priv/vived-engine
git add build.gradle.kts
git commit -m "feat: add maven-publish targeting GitHub Packages"
```

---

## Task 2: Add publish.yml workflow to vived-engine

**Files:**
- Create: `~/Priv/vived-engine/.github/workflows/publish.yml`

> All steps in this task run from `~/Priv/vived-engine`.

- [ ] **Step 1: Create publish.yml**

```yaml
name: Publish

on:
  push:
    branches: [main]

permissions:
  contents: read
  packages: write

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: gradle

      - uses: gradle/actions/setup-gradle@v4

      - name: Publish to GitHub Packages
        run: ./gradlew publish
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          GITHUB_ACTOR: ${{ github.actor }}
```

Save to `~/Priv/vived-engine/.github/workflows/publish.yml`.

- [ ] **Step 2: Commit and push**

```bash
cd ~/Priv/vived-engine
git add .github/workflows/publish.yml
git commit -m "ci: publish to GitHub Packages on push to main"
git push origin main
```

- [ ] **Step 3: Watch the workflow run**

```bash
cd ~/Priv/vived-engine && gh run watch --repo VirtusLab/vived-engine
```

Or open: `https://github.com/VirtusLab/vived-engine/actions`

Expected: the `Publish` workflow runs and succeeds (green).

---

## Task 3: Verify package published

- [ ] **Step 1: Confirm package is visible on GitHub**

```bash
gh api /orgs/VirtusLab/packages?package_type=maven | jq '.[].name'
```

Expected: output includes `"vived-engine"`.

Or open: `https://github.com/orgs/VirtusLab/packages?repo_name=vived-engine`

---

## Task 4: Create PAT and add as secret in jvm-daily

> **This is a manual step — must be done in the browser.**
> `jvm-daily` lives at `ArturSkowronski/jvm-daily`. Its auto `GITHUB_TOKEN` cannot read packages from `VirtusLab/vived-engine` because they're in a different org/owner. A Personal Access Token is required.

- [ ] **Step 1: Create a PAT with read:packages scope**

1. Go to https://github.com/settings/tokens/new (classic token)
2. Name: `jvm-daily-read-packages`
3. Scopes: check `read:packages`
4. Expiration: set as appropriate (e.g. 1 year)
5. Click "Generate token" and copy it

- [ ] **Step 2: Add PAT as a repository secret in jvm-daily**

1. Go to https://github.com/ArturSkowronski/jvm-daily/settings/secrets/actions
2. Click "New repository secret"
3. Name: `GH_PACKAGES_TOKEN`
4. Value: paste the PAT from step 1
5. Click "Add secret"

---

## Task 5: Add GitHub Packages repo and external dep to app/build.gradle.kts

**Files:**
- Modify: `app/build.gradle.kts`

> All steps in this task run from the `jvm-daily` repo root.

- [ ] **Step 1: Replace app/build.gradle.kts**

```kotlin
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
```

- [ ] **Step 2: Update settings.gradle.kts — remove the subproject**

Replace the full file:

```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "jvm-daily"
include("app")
```

(Remove `include("vived-engine")` line.)

- [ ] **Step 3: Delete the vived-engine directory**

```bash
rm -rf vived-engine/
```

- [ ] **Step 4: Add local dev credentials to ~/.gradle/gradle.properties**

```bash
grep -q "githubUsername" ~/.gradle/gradle.properties 2>/dev/null || cat >> ~/.gradle/gradle.properties << 'EOF'

# GitHub Packages — read:packages PAT for VirtusLab/vived-engine
githubUsername=<your-github-username>
githubToken=<PAT with read:packages>
EOF
```

Replace `<your-github-username>` and `<PAT with read:packages>` with real values. This file is never committed.

- [ ] **Step 5: Verify local build resolves the external dependency**

```bash
./gradlew classes
```

Expected: BUILD SUCCESSFUL — Gradle downloads `dev.vived:vived-engine:0.1.0-SNAPSHOT` from GitHub Packages.

If it fails with `Could not resolve dev.vived:vived-engine:0.1.0-SNAPSHOT`, check that:
- `~/.gradle/gradle.properties` has the correct `githubUsername` and `githubToken`
- The PAT has `read:packages` scope
- The package was published (Task 3 verified this)

- [ ] **Step 6: Run tests**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts app/build.gradle.kts
git commit -m "feat: consume vived-engine from GitHub Packages, remove local subproject"
```

---

## Task 6: Update jvm-daily CI to use the PAT

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Add credentials env to the build job**

In `.github/workflows/ci.yml`, find the `build` job's steps section. Add `env:` to the `Compile` and `Test` steps, OR add a top-level `env:` block for the entire `build` job, right after `runs-on`:

```yaml
  build:
    name: Build & Test
    runs-on: ubuntu-latest
    env:
      GITHUB_ACTOR: ${{ github.actor }}
      GITHUB_TOKEN: ${{ secrets.GH_PACKAGES_TOKEN }}
```

The full updated `build` job section:

```yaml
  build:
    name: Build & Test
    runs-on: ubuntu-latest
    env:
      GITHUB_ACTOR: ${{ github.actor }}
      GITHUB_TOKEN: ${{ secrets.GH_PACKAGES_TOKEN }}
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: gradle

      - uses: gradle/actions/setup-gradle@v4

      - name: Compile
        run: ./gradlew classes testClasses

      - name: Test
        run: ./gradlew test

      - name: Publish test results
        uses: EnricoMi/publish-unit-test-result-action@v2
        if: always()
        with:
          files: app/build/test-results/test/*.xml

      - name: Build distribution
        run: ./gradlew installDist

      - name: Upload test report
        uses: actions/upload-artifact@v4
        if: failure()
        with:
          name: test-report
          path: app/build/reports/tests/test/
          retention-days: 7
```

Also update the `docker` job (it runs `./gradlew` indirectly via the Dockerfile — but Docker build doesn't run Gradle, so no change needed there unless the Dockerfile calls Gradle).

- [ ] **Step 2: Commit and push**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: pass GH_PACKAGES_TOKEN for resolving vived-engine from GitHub Packages"
git push origin airy-clavicle
```

- [ ] **Step 3: Watch CI pass**

```bash
gh run watch --repo ArturSkowronski/jvm-daily
```

Expected: `Build & Test` job passes (downloads `dev.vived:vived-engine:0.1.0-SNAPSHOT` using the PAT).

---

## Self-Review Checklist

- [x] **Spec coverage:** All spec sections covered — maven-publish (Task 1), publish workflow (Task 2), verify package (Task 3), PAT + secret (Task 4), jvm-daily dep change (Task 5), local dev credentials (Task 5 Step 4), CI credentials (Task 6)
- [x] **No placeholders:** All steps have concrete commands, full file contents, or explicit browser instructions
- [x] **Type consistency:** No code types involved — Gradle DSL and YAML only; version `0.1.0-SNAPSHOT` used consistently across all tasks
- [x] **Cross-org auth:** PAT requirement (not GITHUB_TOKEN) correctly called out for consuming from VirtusLab in ArturSkowronski CI
