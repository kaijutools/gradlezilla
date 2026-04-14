# Gradlezilla — Agent Workflow Spec

## Overview

Gradlezilla is a Dockerfile generator CLI for Android, built with Kotlin and [Clikt](https://ajalt.github.io/clikt/).

## Project Structure

```
gradlezilla/
├── settings.gradle.kts          # Root project, includes :cli module
├── build.gradle.kts             # Root build config (repos, plugin declarations)
├── gradle/
│   └── libs.versions.toml       # Version catalog (all dependency/plugin versions)
└── cli/
    ├── build.gradle.kts         # CLI module build (application plugin, dependencies)
    └── src/main/kotlin/tools/kaiju/gradlezilla/cli/
        └── Main.kt              # Entry point + root CliktCommand
```

## Commands

```bash
# Build
./gradlew build

# Build without tests
./gradlew assemble

# Run (no arguments)
./gradlew :cli:run

# Run with arguments
./gradlew :cli:run --args="<args>"

# Test
./gradlew test

# Build + test
./gradlew check

# Clean
./gradlew clean

# Install distribution locally (produces bin/gradlezilla script)
./gradlew :cli:installDist
# then run: cli/build/install/cli/bin/cli
```

## Tech Stack

| Layer | Library | Version |
|---|---|---|
| Language | Kotlin JVM | 2.0.0 |
| CLI framework | Clikt | 4.2.2 |
| Terminal output | Mordant | bundled with Clikt |
| Build | Gradle | 9.4.1 (via wrapper) |

## Conventions

### Adding a subcommand

1. Create a new `CliktCommand` subclass in the `cli` module
2. Register it in `Gradlezilla` via `subcommands(MyCommand())`

```kotlin
class MyCommand : CliktCommand(help = "Does something useful") {
    override fun run() { ... }
}

class Gradlezilla : CliktCommand(...) {
    init { subcommands(MyCommand()) }
    override fun run() = Unit
}
```

### Adding a dependency

1. Add the version (if new) and library entry to `gradle/libs.versions.toml`
2. Reference it in the relevant `build.gradle.kts` via `libs.<alias>`

```toml
# gradle/libs.versions.toml
[versions]
some-lib = "1.2.3"

[libraries]
some-lib = { module = "com.example:some-lib", version.ref = "some-lib" }
```

```kotlin
// cli/build.gradle.kts
dependencies {
    implementation(libs.someLib)
}
```

### Package naming

All source lives under `tools.kaiju.gradlezilla.<module>` (e.g., `tools.kaiju.gradlezilla.cli`).
