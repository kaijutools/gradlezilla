---
name: gradlezilla-inspect
description: List the Gradle build tasks (targets) of a Gradle/Android project using the gradlezilla CLI, grouped by task group. Use when the user wants to see what build tasks or targets a Gradle project exposes.
---

# gradlezilla inspect

Lists the Gradle build tasks (targets) available in a project, grouped by their
Gradle task group.

## Prerequisites

Build the launcher once from the repo root:

```bash
./gradlew :cli:installDist
```

This produces `cli/build/install/gradlezilla/bin/gradlezilla`.

## Usage

```bash
cli/build/install/gradlezilla/bin/gradlezilla inspect <projectDir>
```

Or run it straight from Gradle without installing:

```bash
./gradlew :cli:run --args="inspect <projectDir>"
```

**Caveat:** `:cli:run` always executes under this project's pinned JDK 17
toolchain and ignores `JAVA_HOME`. If `<projectDir>` needs a different JDK,
use the installed launcher instead — it honors `JAVA_HOME` normally.

## Arguments

- `<projectDir>` — path to the Gradle project root (must exist and be a directory).

## Notes

- Output is grouped by task group, with each task shown as `path - description`.
- If the project cannot be inspected as a Gradle project, the command fails
  with a usage error.
