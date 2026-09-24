# Gradlezilla — Agent Workflow Spec

## Overview

Gradlezilla is a Dockerfile generator CLI for Android, built with Kotlin and [Clikt](https://ajalt.github.io/clikt/).

## Verification rules

- Expected values in `e2e/expected/*.json` are ground truth derived from the target repo's own
  build files (or AGP's documented defaults) at the pinned commit — never copied from
  gradlezilla's own output. A `groundTruthMethod` that mentions a "verified run" is only
  cross-checking that a value stays stable across environments (e.g. with vs. without a local
  Android SDK present); it never substitutes for reading the actual source of truth.
- Every check must be able to fail. `e2e/verify-extraction.sh` fails the workflow step it runs in
  (`matrix-test.yaml`'s "Verify extraction against ground truth" step `exit 1`s on mismatch rather
  than recording one and returning 0) — a check that reports failure but exits 0 is a bug in the
  check, not a passing repo. When you change CI or a verification script, prove the new check can
  actually fail: force a mismatch once (e.g. edit an `e2e/expected/*.json` value) and confirm the
  run goes red before reverting it.
- Never mark a test-plan item done in a PR description or report unless it was actually run.
  Write "not run" and why, rather than assuming a check would have passed.
- Never loosen an expectation (an `e2e/expected/*.json` value, a schema field, an assertion) to
  get a run green. Report the mismatch and stop — a loosened expectation is a silent regression
  wearing a passing badge.

## Project Structure

```
gradlezilla/
├── settings.gradle.kts          # Root project, includes all modules
├── build.gradle.kts             # Root build config (repos, plugin declarations)
├── gradle/
│   └── libs.versions.toml       # Version catalog (all dependency/plugin versions)
├── e2e/
│   ├── determinism.sh           # Full-corpus determinism proof, see "Determinism" below
│   ├── verify-extraction.sh     # Ground-truth + schema + invariant checks, see "Repository matrix"
│   ├── expected/                # Hand-written ground truth per matrix repo (<slug>.json)
│   └── schema/
│       └── generate-json.schema.json  # `--format json` field-shape snapshot
├── .github/workflows/
│   └── matrix-test.yaml         # Repository Matrix Test, see "Repository matrix" below
├── models/                      # Shared data types + the determinism/JDK-resolution machinery:
│   │                             #   GradlezillaHome, PinnedConnection, JdkResolver, JdkSelector,
│   │                             #   DaemonJvmCriteria, AndroidProjectSpec, ExtractionMetadata
│   └── src/main/kotlin/tools/kaiju/gradlezilla/models/
├── inspector/                    # Introspects a Gradle project (GradleProjectInspector) via the
│   │                              # Tooling API, an init script (initscript/), TOML parsing
│   │                              # (versioncatalog/), and daemon JDK discovery (JdkDiscovery,
│   │                              # JdkWindowResolver, DaemonJdk)
│   └── src/main/kotlin/tools/kaiju/gradlezilla/inspector/
├── generator/                    # Turns a spec into a Dockerfile via DockerfileGenerator
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
main checkout), place it in **a sibling directory of the main checkout**, not nested inside it:

```
<parent-dir>/
├── main/            # the primary checkout
├── <name>/          # e.g. fix-no-config-cache
└── <name>/
```

```bash
git worktree add -b <branch> ../<name> [<start-point>]
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

### `generate <projectDir> [--dry-run|-d] [--daemon-jdk <path>] [--format human|json|sarif]`

Inspects the Android project at `<projectDir>`, infers its toolchain
requirements, and writes a `Dockerfile` to the project root. With `--dry-run`
(`-d`) it prints the Dockerfile to stdout instead of writing it. `--daemon-jdk`
points the extraction daemon at a specific JDK home, bypassing auto-discovery
(see "Determinism" below — this is unrelated to `spec.jdkVersion`, which
`JdkResolver` always derives from the project itself). `--format` selects the
output shape (`human` by default; `json` and `sarif` for machine consumption —
see `format/GenerateFormatter.kt` and `format/Sarif.kt`). See `Generate.kt`.

```bash
# Preview without writing (via Gradle)
./gradlew :cli:run --args="generate /path/to/android/app --dry-run"

# Write the Dockerfile (via installed launcher)
cli/build/install/gradlezilla/bin/gradlezilla generate /path/to/android/app

# Machine-readable output
cli/build/install/gradlezilla/bin/gradlezilla generate /path/to/android/app --dry-run --format json
```

### `inspect <projectDir> [--daemon-jdk <path>] [--format human|json|sarif]`

Lists the Gradle build tasks (targets) of the project at `<projectDir>`,
grouped by task group. See `Inspect.kt`.

```bash
./gradlew :cli:run --args="inspect /path/to/gradle/project"
# or
cli/build/install/gradlezilla/bin/gradlezilla inspect /path/to/gradle/project
```

## Generated image model

The Dockerfile `generate` writes is a build **environment**: the JDK and Android SDK packages the
project's evaluated Gradle configuration says it needs, and nothing else (see
`Dockerfile.template` and `AndroidSdkPackages`). Source is bind-mounted at `/workspace`, never
`COPY`'d in, and nothing is built at image-build time — `docker build` only installs the
toolchain; the actual Gradle build happens later, at `docker run`, against the mounted
repository. `--layered` and `LayeredDockerfileGenerator` (a second generator that resolved
dependencies into a cacheable layer separate from source) were removed deliberately in #36 in
favor of this model. Don't reintroduce a copy-source or dependency-layer Dockerfile shape.

`platform-tools` is installed into the image, unpinned, because AGP 9 otherwise fetches it itself
at Gradle build time if it isn't already present under `$ANDROID_HOME` — baking it into the image
at `docker build` time avoids that network fetch (and its result being whatever build is latest
that day) happening again on every `docker run`.

## Native builds (NDK/CMake)

`androidNdkVersion`/`androidCmakeVersion` are emitted only for modules whose *evaluated* AGP
extension has `externalNativeBuild.cmake.path` or `externalNativeBuild.ndkBuild.path` set — see
`ModuleNativeBuild`/`NativeBuildResolver` in `models/NativeBuild.kt`. That's read off the
evaluated model, not build-file text, so modules configured through `build-logic` convention
plugins are covered too. Deliberately excluded as signal:

- Prebuilt `.so` files under `jniLibs` and native libraries inside consumed AARs — AGP only uses
  the NDK to strip these, and merely warns when it's absent.
- `defaultConfig.ndk { abiFilters ... }`.
- `android.ndkVersion` alone — AGP populates it with its own bundled default on every project,
  native or not, which is what made every non-native project pull a ~1GB NDK into its image (see
  #38, and `e2e/expected/timber.json`'s `androidNdkVersion` note, which records the exact prior
  false positive).

The version-catalog fallback path can never emit either: it parses `libs.versions.toml`, which has
no way to see `externalNativeBuild`.

The effect is significant, not cosmetic: for a pure-Kotlin project like Timber, always installing
an NDK produced a 3.24 GB image; not installing one when nothing needs it drops that to 943 MB
(verified locally by building both generated Dockerfiles, with and without an `"ndk;..."` package
added to the `sdkmanager` line).

## Determinism

Gradlezilla's core promise: the same repo commit, generated with the same Gradlezilla version,
produces byte-identical `generate`/`inspect` output *on a given machine* regardless of what else
is happening on it (other Gradle daemons, other JDKs, a warm vs. cold cache). Across *different*
machines, everything in that output is identical too **except** `extractionMetadata`, which
records machine-specific facts (the Gradle user home path, the project cache dir, which JDK the
daemon happened to run under) by design — see `ExtractionMetadata` below. That guarantee took
several dedicated fixes and the same-machine half is proven by `e2e/determinism.sh`, not just
unit tests — read that script before touching anything
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
- **JDK version resolved from project config, never the host JVM** (`JdkResolver`) — the JDK
  version that goes into the generated Dockerfile (`spec.jdkVersion`) is derived from what the
  project declares (daemon JVM criteria → toolchain → bytecode target → AGP minimum, in that
  priority order), not from `java.home` of the process running gradlezilla. There's no flag to
  override this directly.
- **Daemon JDK discovered from the machine, not assumed** (`JdkWindowResolver`, `JdkDiscovery`,
  `JdkSelector`, `DaemonJdk`, all under `inspector/` except `JdkSelector` in `models/`) — a
  completely separate concern from the JDK version above: which JDK actually runs the Tooling
  API connection. Before any Gradle connection exists, `JdkWindowResolver` computes the window of
  acceptable JDK versions purely from files on disk (ceiling from the target's Gradle wrapper via
  `GradleJdkCompatibility`, floor from AGP/daemon-criteria). `JdkDiscovery` then finds every JDK
  plausibly installed on the machine (`JAVA_HOME`, the current JVM, `~/.gradle/jdks`, SDKMAN,
  asdf, mise, and OS-specific well-known locations — see `JdkLocations`), and `JdkSelector`
  deterministically picks one inside that window: `JAVA_HOME` wins if it's in-window, otherwise
  the highest in-window LTS release, with a fixed source-priority tie-break so the pick never
  depends on filesystem iteration order — that determinism is what `e2e/determinism.sh`
  assertion (3) above depends on. If nothing on the machine fits, `DaemonJdk` fails fast, listing
  every candidate it found and a command to install a compatible one, instead of an opaque
  Tooling API error. `--daemon-jdk <path>` bypasses discovery entirely with an explicit path.
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
  (`GradleJdkCompatibility`). That 5.0 floor is design intent, checked only by API-availability
  inspection — the range actually exercised end-to-end against real distributions is 8.0 through
  9.7.1: `InitScriptGradleVersionCompatTest` runs the packaged `extractor.gradle` through TestKit
  against Gradle 8.0 (the exact line that regressed in #28), and the Repository Matrix
  (`matrix-test.yaml`, see "Repository matrix" below) runs the full CLI against real Android repos
  on wrapper versions from 8.0 (Signal, Sunflower) through 9.7.1 (Now In Android, Timber,
  Wikipedia, NDK Samples). Any init-script API newer than Gradle 5.0 must still stay behind an
  explicit `GradleVersion.current() >= ...` guard with a fallback for older versions; an unguarded
  newer API silently breaks extraction for every project on an older Gradle instead of failing a
  test. The target's Gradle version (used to decide whether to add `--no-configuration-cache` at
  all) is read from the project's
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
- Don't run `e2e/determinism.sh` concurrently with another Gradle build on the same machine.
  `build_cli()` runs `./gradlew :cli:installDist` against the ambient, un-overridden Gradle user
  home — only the target-extraction runs later in the script get an isolated
  `GRADLEZILLA_GRADLE_HOME`. Two builds sharing that ambient home (another `e2e/determinism.sh`,
  or your own `./gradlew` in another terminal) can hit real Gradle daemon/cache contention and
  fail spuriously.
- `e2e/determinism.sh`'s `cleanup()` trap fires on any exit and deletes `WORK_DIR` — including the
  per-run stderr logs — even on failure, so a failing run's diagnostics are gone by the time you
  see the failure message ([#42](https://github.com/kaijutools/gradlezilla/issues/42)). Until
  it's fixed, comment out `trap cleanup EXIT` locally if you need to inspect a failure.

### Repository matrix

`.github/workflows/matrix-test.yaml` runs the full CLI against a small corpus of real, pinned
Android repos on every tag push (and on demand via `workflow_dispatch`), verified by
`e2e/verify-extraction.sh` against `e2e/expected/<slug>.json`.

To add a corpus repo:

1. Pick a commit SHA to pin to — never a branch, since the corpus must stay reproducible.
2. Add it to the `matrix.repo` list in `matrix-test.yaml` (`name`, `slug`, `url`, `sha`).
3. Write `e2e/expected/<slug>.json` by hand from that commit's actual build files (see
   "Verification rules" above) — `groundTruth`, `groundTruthMethod`, and `pinRationale` are all
   required; `pinRationale` should say what real-world condition this repo is pinned to exercise
   (e.g. an old Gradle version, a genuine NDK build).

`e2e/schema/generate-json.schema.json` is a hand-written snapshot of `--format json`'s field
names/types (required vs. optional, per each field's Kotlin default) — `verify-extraction.sh`
fails a run whose JSON has a key outside it, or is missing a required key, so an accidental
rename or dropped field becomes a red matrix run instead of a silent regression. Regenerate it by
hand whenever a spec field's shape or default-ness changes, in the same PR as that change.

The matrix's "Prime target repo" step deliberately runs the target repo's own `./gradlew help`
under JDK 17 while the runner's ambient `JAVA_HOME` (and the `gradlezilla` steps that follow) stay
on JDK 21 — Gradle 8.0 (Signal, Sunflower) cannot run its daemon on JDK 21 at all, so this is what
actually exercises `JdkDiscovery`'s fallback to an alternate JDK on the runner image, rather than
every repo just working on the ambient JDK by coincidence.

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

## Pull requests

- Titles follow [Conventional Commits](https://www.conventionalcommits.org/)
  (`fix(inspector): ...`, `feat(generator)!: ...`) — see recent history for the pattern. Use `!`
  only for a change a user of the CLI or generated Dockerfile would notice as breaking (a flag
  removed, an output shape changed, a documented guarantee narrowed) — not for internal
  refactors.
- Don't retitle a PR after opening it: this repo squash-merges, and the squash commit takes the
  PR's title verbatim.
- The PR description must state what was actually verified (commands run, and their output or
  exit code), not just what was intended — see "Verification rules" above.
