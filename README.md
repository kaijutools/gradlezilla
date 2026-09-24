# 🦖 Gradlezilla

**Correct, reproducible Android build environments, derived from your Gradle build.**

Writing and maintaining Dockerfiles for Android CI/CD is notoriously painful. You have to perfectly pin the JDK, Android SDK, Build Tools, Command Line Tools, and NDK versions, or your build crashes. 

Gradlezilla introspects your Android project — via a real Gradle Tooling API connection, not text parsing — and generates a Dockerfile for exactly the toolchain it needs. Extraction is deterministic: the same commit and Gradlezilla version produce the same result on a given machine, isolated from other Gradle daemons and caches already running on it (see "Determinism" below).

## 🚀 Installation

The easiest way to install Gradlezilla on macOS or Linux is via Homebrew:

```bash
brew tap kaijutools/tap
brew install gradlezilla
```

*(You can also download the latest pre-compiled binary ZIP from the [Releases](https://github.com/kaijutools/gradlezilla/releases) page).*

## 🛠️ Usage

Navigate to the root of your Android repository and run the `generate` command:

```bash
cd /path/to/your/android/app
gradlezilla generate .
```

Gradlezilla asks Gradle itself for your project's evaluated configuration — not your `build.gradle` / `build.gradle.kts` files as text — and writes a `Dockerfile` directly to your project root with exactly the versions that configuration resolves to.

### Options & Flags

* **Dry Run:** Preview the generated Dockerfile in your terminal without writing it to disk.
  ```bash
  gradlezilla generate . --dry-run
  # or
  gradlezilla generate . -d
  ```

* **Machine-Readable Output:** For CI pipelines and other tooling, emit `json` or `sarif` instead of the human-readable summary:
  ```bash
  gradlezilla generate . --dry-run --format json
  ```
  `--format json` is meant to be consumed directly by scripts and AI agents, not just humans — it's a stable, schema-checked shape (see `AGENTS.md`'s "Repository matrix" section), not scraped human output.

Gradlezilla always derives the required JDK version from what your project itself declares — a
Gradle daemon JVM criteria pin, toolchain declarations, bytecode targets, or your AGP version's
minimum — never from whichever JVM happens to launch it. There's no manual `--jdk` override for
that. Separately, to actually run your project's Gradle daemon during inspection, Gradlezilla
searches your machine (`JAVA_HOME`, the current JVM, SDKMAN, asdf, mise, and OS-specific install
locations) for a JDK compatible with your project's Gradle wrapper, and fails fast — listing
every JDK it found plus a command to install a compatible one — if nothing on the machine fits.
Use `--daemon-jdk <path>` to point it at a specific JDK instead of relying on discovery.

### Inspecting a Project

To list a project's Gradle build tasks (grouped by task group) without generating a Dockerfile:

```bash
gradlezilla inspect .
```

## 🏗️ How to Use Your Generated Dockerfile

The generated image is a build **environment**, not a build artifact: it contains the
pinned JDK and Android SDK packages your project needs, and nothing else. Your source
code is never copied into the image — mount your repository in at `/workspace` and run
Gradle there:

```bash
# 1. Build the environment image — no build context needed, since nothing is copied in
docker build -t my-android-env - < Dockerfile

# 2. Run Gradle inside it, against your mounted repository and a persistent Gradle cache
docker run --rm \
  -v "$PWD:/workspace" \
  -v gradlezilla-cache:/root/.gradle \
  my-android-env ./gradlew assembleDebug
```

Because the repository is mounted rather than copied, the APK lands directly at
`build/outputs/apk/debug` on your host — no `docker cp` needed. This also means the
image is stable across source-only commits: it only changes when a base version (JDK,
`compileSdk`, build tools, NDK) changes, so it's a good fit for caching in CI.

### GitHub Actions

Use the image directly as the job's `container` — the mount is implicit, since the repo
is already checked out into the workspace:

```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    container: ghcr.io/your-org/my-android-env:latest
    steps:
      - uses: actions/checkout@v6
      - run: ./gradlew assembleDebug --no-daemon
```

## 🧠 How it Works (Under the Hood)

Parsing Gradle files with Regex (or hand-rolled AST parsing) is a fool's errand due to the complexity of the Kotlin DSL and version catalogs. So instead of re-implementing Gradle's own evaluation logic, Gradlezilla asks Gradle itself — via the [Tooling API](https://docs.gradle.org/current/userguide/tooling_api.html), the same mechanism Android Studio uses to sync a project.

Gradlezilla uses a **Chain of Responsibility** of extractors:
1. **Init-Script Extraction (primary path):** An init script hooks the target project's own Gradle evaluation and emits its exact AGP version, `compileSdk`, `buildToolsVersion`, and toolchain/bytecode-target facts straight from the evaluated model — not a text-based guess.
2. **Version Catalog Fallback:** If that doesn't yield a usable AGP version, it falls back to parsing `gradle/libs.versions.toml` directly.
3. **JDK Resolution:** The required JDK is derived from what the project declares — a Gradle daemon JVM criteria pin, toolchain declarations, bytecode targets, or the AGP minimum — never from whichever JVM happens to be running Gradlezilla itself.
4. **Environment Generation:** These requirements are synthesized into a dynamic `sdkmanager` bash command that installs only what your project strictly requires — nothing more, nothing less.

The NDK and CMake are part of that last step, but only when a module actually compiles native
code — i.e. its evaluated build has `externalNativeBuild.cmake.path` or `.ndkBuild.path` set.
Android Gradle Plugin stamps every project with a default `ndkVersion` whether or not it builds
any native code, so that field alone is never evidence of anything; a prebuilt `.so` under
`jniLibs` doesn't count either. Getting this right matters in practice, not just in principle: for
a pure-Kotlin project like [Timber](https://github.com/JakeWharton/timber), installing an NDK
unconditionally produced a 3.24 GB image — skipping it when nothing needs one drops that to
943 MB.

Running a real Gradle build sounds like it should be slow and flaky — daemon crashes, cache collisions, output that depends on whatever else is running on your machine. Gradlezilla avoids that by giving every invocation its own isolated Gradle user home and project cache directory, pinning the JDK explicitly instead of trusting the ambient one, and disabling Gradle's configuration cache for the extraction run so a stale cache entry can never silently skip extraction (see "Known Limitations" below).

### Determinism

Gradlezilla's contract: the same repository commit, generated with the same Gradlezilla version,
produces an identical spec on the same machine — regardless of what else is running on it (other
Gradle daemons, other JDKs, a warm vs. cold cache). Across *different* machines, everything in
that spec is identical too **except** `extractionMetadata`, which records machine-specific facts
(the Gradle user home path, the project cache dir, which JDK the daemon happened to run under) by
design. See `AGENTS.md`'s "Determinism" section for the mechanisms behind this and
`e2e/determinism.sh` for the proof.

## 📊 Matrix Test Status

Results from the latest [Repository Matrix Test](.github/workflows/matrix-test.yaml) run on `main`.

<!-- MATRIX-TABLE:START -->
Last verified at commit [`fdecfe2`](https://github.com/kaijutools/gradlezilla/commit/fdecfe2cfc9b398075250aa608e7186c962739a4).

| Repository | Status |
|---|---|
| Sunflower | ✅ Success |
| Now In Android | ✅ Success |
| Timber | ✅ Success |
| Signal | ✅ Success |
| Wikipedia | ✅ Success |

<!-- MATRIX-TABLE:END -->

## ⚠️ Known Limitations

Gradlezilla runs every Gradle Tooling API connection against an isolated, persistent Gradle
user home (`~/.gradlezilla/gradle-home`, overridable via `GRADLEZILLA_GRADLE_HOME`) so its
daemon never gets mixed up with ones spawned by Android Studio or other projects. The very
first run creates this home and prints a one-line notice to stderr; that run — and any later
run that needs a Gradle distribution it hasn't downloaded yet — will be noticeably slower
while the distribution downloads. Once a given Gradle version has been used once, subsequent
runs against it reuse the cached distribution.

CI users should cache the `GRADLEZILLA_GRADLE_HOME` directory between runs to avoid paying
the warm-up cost on every job.

Gradlezilla is tested against Gradle 8.0 through 9.7.1 via the [Repository Matrix
Test](.github/workflows/matrix-test.yaml) against real Android repos; older versions may work but
aren't verified (see `AGENTS.md` for the contributor-facing detail on this range).

The generated Dockerfile's base image (`eclipse-temurin:<jdk>-jdk-jammy`) is a floating tag, not
a digest pin, and the `platform-tools` SDK package has no version you can pin at all — both can
silently pick up a newer build on a fresh `docker build`. Pin the base image by digest yourself
or vendor the generated Dockerfile if you need a byte-for-byte reproducible image.

Because your repository is bind-mounted rather than copied, anything in it — including a host
`local.properties` with a host-specific `sdk.dir` — is visible inside the container exactly as it
sits on your host. Gradlezilla doesn't sandbox or strip it.

JVM/daemon memory settings (`org.gradle.jvmargs`, `kotlin.daemon.jvmargs`, etc.) normally live in
your `~/.gradle/gradle.properties` on the host, which isn't baked into the generated image. Pass
them explicitly with `-P` on the `./gradlew` invocation, or bind-mount your own
`~/.gradle/gradle.properties` into the container's Gradle user home.

The generated image runs as root by default, so build outputs (`build/`, `.gradle/`) written back
through the bind mount end up root-owned on Linux hosts. Run with `--user "$(id -u):$(id -g)"`
and give that user a writable Gradle home (e.g. a cache volume you `chown` first, or
`-e GRADLE_USER_HOME=/workspace/.gradle-home`).

Don't point two concurrently running containers at the same Gradle cache volume — Gradle's own
locking assumes one daemon per `GRADLE_USER_HOME`, and concurrent writers can corrupt the cache.

JDK discovery for the extraction daemon (see "Usage" above) is best-effort on Windows: it checks
`JAVA_HOME`, the current JVM, and common installer locations, but hasn't been verified against
Windows JDK layouts the way the macOS/Linux paths have.

## 🤝 Contributing

Pull requests are welcome! If Gradlezilla fails to parse a specific repository structure, please open an issue with a link to the public repo or a snippet of the `build.gradle` file.

1. Clone the repository.
2. Build the CLI locally: `./gradlew :cli:installDist`
3. Run your local build: `./cli/build/install/gradlezilla/bin/gradlezilla --help`

## 📄 License

Gradlezilla is released under the [MIT License](LICENSE).