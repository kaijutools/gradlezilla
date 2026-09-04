# Gradlezilla — Agent Workflow Spec

## Overview

Gradlezilla is a Dockerfile generator CLI for Android, built with Kotlin and [Clikt](https://ajalt.github.io/clikt/).

## Project Structure

```
gradlezilla/
├── settings.gradle.kts          # Root project, includes all modules
├── build.gradle.kts             # Root build config (repos, plugin declarations)
├── gradle/
│   └── libs.versions.toml       # Version catalog (all dependency/plugin versions)
├── models/                      # Shared data types (e.g. AndroidProjectSpec)
├── inspector/                   # Introspects a Gradle project (GradleProjectInspector)
├── generator/                   # Turns a spec into a Dockerfile (DockerfileGenerator)
└── cli/                         # Application module (application plugin)
    ├── build.gradle.kts
    └── src/main/kotlin/tools/kaiju/gradlezilla/cli/
        ├── Main.kt              # Entry point + root CliktCommand, registers subcommands
        ├── Generate.kt          # `generate` subcommand
        ├── Inspect.kt           # `inspect` subcommand
        └── Version.kt           # `version` subcommand
```

## Developer commands (Gradle)

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

# Install distribution locally (produces the gradlezilla launcher script)
./gradlew :cli:installDist
# then run: cli/build/install/gradlezilla/bin/gradlezilla
```

## gradlezilla CLI commands

These are the runtime subcommands exposed by the built application. Run them
either through the Gradle `:cli:run` task or via the installed launcher
(`cli/build/install/gradlezilla/bin/gradlezilla`, after `./gradlew :cli:installDist`).

### `generate <projectDir> [--dry-run|-d]`

Inspects the Android project at `<projectDir>`, infers its toolchain
requirements, and writes a `Dockerfile` to the project root. With `--dry-run`
(`-d`) it prints the Dockerfile to stdout instead of writing it. See `Generate.kt`.

```bash
# Preview without writing (via Gradle)
./gradlew :cli:run --args="generate /path/to/android/app --dry-run"

# Write the Dockerfile (via installed launcher)
cli/build/install/gradlezilla/bin/gradlezilla generate /path/to/android/app
```

### `inspect <projectDir>`

Lists the Gradle build tasks (targets) of the project at `<projectDir>`,
grouped by task group. See `Inspect.kt`.

```bash
./gradlew :cli:run --args="inspect /path/to/gradle/project"
# or
cli/build/install/gradlezilla/bin/gradlezilla inspect /path/to/gradle/project
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
2. Register it in `main()` via `.subcommands(MyCommand())` alongside the existing ones

```kotlin
class MyCommand : CliktCommand(help = "Does something useful", name = "my-command") {
    override fun run() { ... }
}

fun main(args: Array<String>) =
    Main()
        .subcommands(
            Version(),
            Inspect(),
            Generate(),
            MyCommand(),
        ).main(args)
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

### Adding a new module

1. Create `<module>/build.gradle.kts` and apply the standard plugins:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}
```

2. Create the source directory following the package naming convention:
   `<module>/src/main/kotlin/tools/kaiju/gradlezilla/<module>/`

3. Register the module in `settings.gradle.kts`:

```kotlin
include(":<module>")
```

4. If `:cli` (or another module) depends on it, add a project dependency:

```kotlin
// cli/build.gradle.kts
dependencies {
    implementation(project(":<module>"))
}
```

### Package naming

All source lives under `tools.kaiju.gradlezilla.<module>` (e.g., `tools.kaiju.gradlezilla.cli`).
