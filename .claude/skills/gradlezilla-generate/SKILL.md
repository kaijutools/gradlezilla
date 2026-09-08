---
name: gradlezilla-generate
description: Generate a production-ready Dockerfile for an Android/Gradle project using the gradlezilla CLI. Use when the user wants to create, generate, or preview a Dockerfile for an Android project, or set up a containerized Android build environment.
---

# gradlezilla generate

Generates a Dockerfile for an Android project by introspecting its Gradle
toolchain (JDK, Android SDK, build tools, command-line tools, NDK) and
synthesizing a matching immutable build environment.

## Prerequisites

Build the launcher once from the repo root:

```bash
./gradlew :cli:installDist
```

This produces `cli/build/install/gradlezilla/bin/gradlezilla`.

## Usage

```bash
# Preview the Dockerfile without writing it (recommended first step)
cli/build/install/gradlezilla/bin/gradlezilla generate <projectDir> --dry-run

# Write the Dockerfile to <projectDir>/Dockerfile
cli/build/install/gradlezilla/bin/gradlezilla generate <projectDir>
```

You can also run it straight from Gradle without installing:

```bash
./gradlew :cli:run --args="generate <projectDir> --dry-run"
```

**Caveat:** `:cli:run` always executes under this project's pinned JDK 17
toolchain and ignores `JAVA_HOME`. If `<projectDir>` needs a different JDK,
use the installed launcher instead — it honors `JAVA_HOME` normally.

## Arguments & flags

- `<projectDir>` — path to the Android project root (must exist and be a directory).
- `--dry-run`, `-d` — print the Dockerfile to stdout instead of writing it to disk.

## Notes

- Without `--dry-run`, the command writes/overwrites `<projectDir>/Dockerfile`.
  Prefer `--dry-run` first to review the output.
- If the project cannot be inspected as a Gradle project, the command fails
  with a usage error.
