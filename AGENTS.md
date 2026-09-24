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
├── e2e/
│   └── determinism.sh           # Full-corpus determinism proof, see "Determinism" below
├── models/                      # Shared data types + the determinism/JDK-resolution machinery:
│   │                             #   GradlezillaHome, PinnedConnection, JdkResolver, JdkSelector,
│   │                             #   DaemonJvmCriteria, AndroidProjectSpec, ExtractionMetadata
│   └── src/main/kotlin/tools/kaiju/gradlezilla/models/
├── inspector/                    # Introspects a Gradle project (GradleProjectInspector) via the
│   │                              # Tooling API, an init script (initscript/), TOML parsing
│   │                              # (versioncatalog/), and daemon JDK discovery (JdkDiscovery,
│   │                              # DaemonJdk)
│   └── src/main/kotlin/tools/kaiju/gradlezilla/inspector/
├── generator/                    # Turns a spec into a build-environment Dockerfile
│   │                              # (DockerfileGenerator)
│   └── src/main/kotlin/tools/kaiju/gradlezilla/generator/
└── cli/                          # Application module (application plugin)
    ├── build.gradle.kts
    └── src/main/kotlin/tools/kaiju/gradlezilla/cli/
        ├── Main.kt              # Entry point + root CliktCommand; see the note on main() below
        ├── Generate.kt          # `generate` subcommand
        ├── Inspect.kt           # `inspect` subcommand
        ├── Version.kt           # `version` subcommand
        └── format/              # human/json/sarif output formatting shared by generate & inspect
```

## Git worktrees

If you create a `git worktree` for this repo (e.g. to work on something in parallel with the
main checkout), place it as a **sibling** of `main`, not nested inside it:

```
~/work/Kaiju/gradlezilla/
├── main/            # the primary checkout
├── <name>/          # e.g. fix-no-config-cache
└── <name>/
```

```bash
git worktree add -b <branch> ~/work/Kaiju/gradlezilla/<name> [<start-point>]
```

Do **not** create worktrees under `main/.claude/worktrees/` (or anywhere else inside `main`'s
own working tree) — a worktree nested inside the repo it was branched from shows up as an
untracked directory in `git status`/`ls` of the main checkout, which is confusing and easy to
accidentally `git add`. If one already exists there, relocate it with `git worktree move`
(never a plain `mv` — that leaves git's internal worktree metadata pointing at the old path).

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

> **Note:** `:cli:run` always executes under this project's pinned JDK 17
> toolchain (see root `build.gradle.kts`) and ignores `JAVA_HOME`. Prefer the
> installed launcher when running `generate`/`inspect` against a project that
> may need a different JDK — it has no toolchain of its own and honors
> `JAVA_HOME` normally.

### `generate <projectDir> [--dry-run|-d] [--format human|json|sarif]`

Inspects the Android project at `<projectDir>`, infers its toolchain
requirements, and writes a `Dockerfile` to the project root. With `--dry-run`
(`-d`) it prints the Dockerfile to stdout instead of writing it. `--format`
selects the output shape (`human` by default; `json` and `sarif` for machine
consumption — see `format/GenerateFormatter.kt` and `format/Sarif.kt`). See
`Generate.kt`.

```bash
# Preview without writing (via Gradle)
./gradlew :cli:run --args="generate /path/to/android/app --dry-run"

# Write the Dockerfile (via installed launcher)
cli/build/install/gradlezilla/bin/gradlezilla generate /path/to/android/app

# Machine-readable output
cli/build/install/gradlezilla/bin/gradlezilla generate /path/to/android/app --dry-run --format json
```

### `inspect <projectDir> [--format human|json|sarif]`

Lists the Gradle build tasks (targets) of the project at `<projectDir>`,
grouped by task group. See `Inspect.kt`.

```bash
./gradlew :cli:run --args="inspect /path/to/gradle/project"
# or
cli/build/install/gradlezilla/bin/gradlezilla inspect /path/to/gradle/project
```

## Determinism

Gradlezilla's core promise is that `generate`/`inspect` produce byte-identical output for the
same project regardless of what else is happening on the host machine (other Gradle daemons,
other JDKs, a warm vs. cold cache). That guarantee took several dedicated fixes and is proven
by `e2e/determinism.sh`, not just unit tests — read that script before touching anything
Tooling-API-related, and run it after any change near `models/GradlezillaHome.kt`,
`inspector/GradleProjectInspector.kt`, or `inspector/initscript/InitScriptExtractor.kt`:

```bash
./gradlew :cli:installDist
e2e/determinism.sh                          # small default corpus (Sunflower, Timber)
GRADLEZILLA_E2E_REPOS='...' e2e/determinism.sh   # full OSS corpus, see the script header
```

It clones real Android repos and asserts three things per repo: (1) three consecutive runs
produce byte-identical `--format json` output, (2) that output stays valid JSON with nothing
else on stdout, and (3) the spec/Dockerfile are identical whether gradlezilla is launched under
one JDK or another — all while an *ambient* Gradle daemon (a different JDK, a different Gradle
user home) is deliberately left running to simulate Android Studio or another project already
using the machine.

The mechanisms behind that guarantee, in `models/`:

- **Isolated Gradle user home** (`GradlezillaHome`) — every Tooling API connection uses a
  dedicated, persistent home at `~/.gradlezilla/gradle-home` (override with
  `GRADLEZILLA_GRADLE_HOME`), never the ambient `~/.gradle`, so gradlezilla's daemon never gets
  matched against — or its behavior perturbed by — daemons other tools left running. Persistent
  rather than temp, so distributions aren't re-downloaded every run; the first run against a
  fresh home is slower and prints a notice to stderr. CI should cache this directory.
- **Isolated per-project cache dir** (`GradlezillaHome.projectCacheDir`) — `--project-cache-dir`
  is pointed at a directory keyed by a SHA-256 hash of the target project's canonical path,
  under the gradlezilla home, instead of Gradle's default (the target project's own `.gradle/`).
  Otherwise every gradlezilla run would write configuration-cache/task-history state into
  someone else's repository.
- **Pinned JDK per connection** (`PinnedConnection`) — every operation explicitly sets
  `setJavaHome(...)`; gradlezilla never trusts whichever JVM happens to be running the daemon
  process. The Tooling API's `withArguments` *replaces* rather than accumulates, which used to
  mean every call site needing its own arguments had to remember to re-append
  `--project-cache-dir` — an easy thing to silently drop. It is now structurally impossible:
  `withArguments` is called in exactly one place (`RealPinnedConnection.pinned`), and a caller
  adds arguments by passing them to `PinnedConnection.build(extraArguments)`, which *appends*
  them to `pinnedArguments`. Don't reintroduce a `withArguments` call outside `pinned`.
- **JDK version resolved from project config, never the host JVM** (`JdkResolver`) — the
  required JDK (`spec.jdkVersion`) is derived from what the project declares (daemon JVM
  criteria → toolchain → bytecode target → AGP minimum, in that priority order), not from
  `java.home` of the process running gradlezilla. This is entirely separate from which JVM
  actually runs the extraction daemon: `DaemonJdk.resolve` discovers JDKs already installed on
  the machine (`JdkDiscovery`) and picks one that falls inside the window the target's Gradle
  wrapper version supports (`JdkSelector`, `JdkWindowResolver`, `GradleJdkCompatibility`) —
  discovery, not a manual pin. When nothing on the machine is compatible, it fails fast with the
  candidates it considered and an install suggestion (e.g. `sdk install java 21-tem`), not a
  "set `JAVA_HOME`" instruction. `--daemon-jdk` bypasses discovery entirely with an explicit
  JDK home.
- **Configuration cache deliberately disabled for extraction, not busted per run**
  (`buildPinnedArguments` in `models/GradlezillaHome.kt`) — because the project-cache-dir above is
  persistent, a config-cache entry from a *prior* gradlezilla run against the same project would
  silently skip the init script's data-emitting hooks entirely on the next run. Every operation on
  a `PinnedConnection` therefore carries **both** of:
  ```
  --no-configuration-cache
  -Dorg.gradle.unsafe.isolated-projects=false
  ```
  Both, together. Isolated Projects really does mandate the configuration cache and hard-fails on
  `--no-configuration-cache` on its own — but Isolated Projects can itself be switched off in the
  same invocation, and the pair succeeds where either alone does not. Verified by hand against
  nowinandroid, which enables Isolated Projects:
  ```bash
  ./gradlew help --no-configuration-cache                                              # FAILS
  ./gradlew help --no-configuration-cache -Dorg.gradle.unsafe.isolated-projects=false  # SUCCEEDS
  ```
  This replaced an earlier `-DgradlezillaCacheBust=<uuid>` token read via
  `providers.systemProperty(...)` at the init script's top level. That token worked by changing a
  configuration-cache *input* every run, which meant it **stored a new cache entry on every
  invocation** — `~/.gradlezilla/.../project-caches` grew without bound. It also put a
  `providers` read at init-script top level, which is what caused the Gradle 8.0 regression in
  #28/#35. Disabling the cache writes no entry at all, so that directory now stays flat.
  `--no-configuration-cache` is version-gated on the *target* project's Gradle version (>= 6.6,
  when the option was introduced): Gradle fails the build outright on an unknown command-line
  option, verified against real 6.0 and 6.5 distributions, and target support goes back to 5.0
  (`GradleJdkCompatibility`). That version is read from the project's
  `gradle/wrapper/gradle-wrapper.properties` (`wrapperGradleVersion`), *not* asked of the daemon.
  A `BuildEnvironment` probe would have to run without the very opt-out it is deciding on, and on
  a project that sets `org.gradle.configuration-cache=true` that probe stores an entry of its own
  — observed against nowinandroid, where it put back exactly the per-run cache growth this change
  removes. No wrapper, or an unparseable one, omits the flag: the safe direction. The `-D` needs
  no gate; an unrecognised system property is ignored on every version.

  The resulting argument list is reported in `extractionMetadata.extractionArgs`, so `--format
  json` shows exactly what extraction ran with. The init script's own path is redacted out of it
  (`redactInitScriptPath`) — it's a fresh temp file every run, and that output has to stay
  byte-identical across runs.
- **`GRADLEZILLA_DEBUG=1`** — dumps the full exception cause chain for a failed extractor to
  stderr; the formatted error message alone often hides the real root cause underneath Gradle's
  wrapper exceptions.

### Gotchas

- `GradlezillaHome.prepare()` refuses to write into `GRADLEZILLA_GRADLE_HOME` (or the default
  `~/.gradlezilla/gradle-home`) if that directory already has unrelated content — it only
  recognizes a home it marked itself (`.gradlezilla-owned`). Don't point that env var at an
  existing `~/.gradle`.
- Distribution-download progress writes straight to `System.out` from the Tooling API's
  connector bootstrap, bypassing `setStandardOutput`. `PinnedConnection.withConnection`
  redirects process-wide `System.out` to stderr for the life of the connection so this can never
  land ahead of `--format json`/`sarif` output on stdout — if you add a new code path that opens
  its own `GradleConnector` outside `PinnedConnection`, you lose this protection.
- `:cli:run` always executes under this project's own pinned JDK 17 toolchain and ignores
  `JAVA_HOME` (see the note under "gradlezilla CLI commands" above) — don't use it to
  sanity-check JDK-resolution behavior; use the installed launcher.

## Tech Stack

| Layer | Library | Version |
|---|---|---|
| Language | Kotlin JVM | 2.2.0 |
| CLI framework | Clikt | 4.2.2 |
| Terminal output | Mordant | bundled with Clikt |
| Build | Gradle | 9.4.1 (via wrapper) |

## Conventions

### Adding a subcommand

1. Create a new `CliktCommand` subclass in the `cli` module
2. Register it in `main()`'s `.subcommands(...)` call alongside the existing ones

```kotlin
class MyCommand : CliktCommand(help = "Does something useful", name = "my-command") {
    override fun run() { ... }
}
```

```kotlin
// Main.kt
val command = Main().subcommands(Version(), Inspect(), Generate(), MyCommand())
```

> **Note:** `main()` doesn't just call `command.main(args)`. It first sniffs `--format` straight
> out of argv (`detectFormat`, before Clikt parses anything) so that parse/usage errors and
> uncaught exceptions can be rendered through the same `human`/`json`/`sarif` `ErrorFormatter` as
> a command's own output, instead of always printing as plain text. If your new subcommand adds
> its own `--format`-sensitive output, route it through `format/` rather than `echo`-ing
> ad hoc — see `Generate.kt`/`Inspect.kt` for the pattern.

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
