# vived-engine: GitHub Packages Publishing

**Date:** 2026-03-31
**Status:** Approved

## Problem

`jvm-daily` embeds `vived-engine` as a local Gradle subproject (`include("vived-engine")`). This couples the two codebases — changes to `vived-engine` must be made inside `jvm-daily`, and the canonical source (`~/Priv/vived-engine` / `VirtusLab/vived-engine`) diverges over time.

## Goal

Decouple `vived-engine` from `jvm-daily` by publishing it as a versioned Maven artifact to GitHub Packages. `jvm-daily` consumes it as a standard external dependency.

## Solution: GitHub Packages (Maven)

### Coordinates

```
group:    dev.vived
artifact: vived-engine
version:  0.1.0-SNAPSHOT
```

---

## Changes to `VirtusLab/vived-engine`

### 1. `build.gradle.kts` — add `maven-publish`

Add the `maven-publish` plugin and a `publishing` block that targets GitHub Packages:

```kotlin
plugins {
    // existing plugins ...
    `maven-publish`
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
                username = project.findProperty("githubUsername") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("githubToken") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

### 2. `.github/workflows/publish.yml` — publish on push to main

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

---

## Changes to `jvm-daily`

### 1. `settings.gradle.kts` — remove subproject

Remove:
```kotlin
include("vived-engine")
```

### 2. Delete `vived-engine/` directory

The entire local copy is removed.

### 3. `app/build.gradle.kts` — switch to external dependency

Replace:
```kotlin
implementation(project(":vived-engine"))
```
With:
```kotlin
implementation("dev.vived:vived-engine:0.1.0-SNAPSHOT")
```

### 4. Root `build.gradle.kts` (or `app/build.gradle.kts`) — add GitHub Packages repository

```kotlin
repositories {
    mavenCentral()
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/VirtusLab/vived-engine")
        credentials {
            username = project.findProperty("githubUsername") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("githubToken") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
```

### 5. `.github/workflows/ci.yml` — pass credentials

Add to the build job's `env`:
```yaml
env:
  GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
  GITHUB_ACTOR: ${{ github.actor }}
```

> **Note:** `GITHUB_TOKEN` in `jvm-daily` CI can read packages from `VirtusLab/vived-engine` only if the package's visibility is set to internal/org or the token has `read:packages`. If not, a PAT with `read:packages` stored as `secrets.GH_PACKAGES_TOKEN` must be used instead.

### 6. Local dev credentials — `~/.gradle/gradle.properties`

Add (not committed):
```properties
githubUsername=<your-github-username>
githubToken=<PAT with read:packages>
```

---

## Credential Strategy

| Context | Username | Token |
|---|---|---|
| `vived-engine` CI (publish) | `GITHUB_ACTOR` | `GITHUB_TOKEN` (auto, needs `packages: write`) |
| `jvm-daily` CI (consume) | `GITHUB_ACTOR` | `GITHUB_TOKEN` if same org, else PAT stored as secret |
| Local dev | `~/.gradle/gradle.properties` | PAT with `read:packages` |

---

## Out of Scope

- Version bumping / release tagging strategy (using SNAPSHOT for now)
- Publishing `runner` subproject of `vived-engine`
- Migrating `jvm-daily` app-specific code that accidentally lives in `vived-engine`
