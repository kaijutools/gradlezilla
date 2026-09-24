# 🦖 Gradlezilla

**Zero-config, highly-optimized Dockerfiles for Android projects in under 5 seconds.**

Writing and maintaining Dockerfiles for Android CI/CD is notoriously painful. You have to perfectly pin the JDK, Android SDK, Build Tools, Command Line Tools, and NDK versions, or your build crashes. 

Gradlezilla introspects your Android project, extracts the exact toolchain requirements, and generates a production-ready, immutable Docker environment. No Gradle Daemon crashes. No host bleed-through. Just reliable builds.

![Gradlezilla Demo](docs/demo.gif)

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

Gradlezilla will analyze your `build.gradle` / `build.gradle.kts` files, infer the correct versions, and write a perfectly formatted `Dockerfile` directly to your project root.

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

Gradlezilla always derives the required JDK version from what your project itself declares — a
Gradle daemon JVM criteria pin, toolchain declarations, bytecode targets, or your AGP version's
minimum — never from whichever JVM happens to launch it. There's no manual `--jdk` override: if
your project's Gradle wrapper doesn't support the JDK you're currently running, Gradlezilla fails
fast with the `JAVA_HOME` to set instead of silently building under the wrong one.

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
# 1. Build the environment image — the Dockerfile needs no build context (nothing
#    is COPYed in), so build it from stdin and skip sending your repo as context
docker build -t my-android-env - < Dockerfile

# 2. Run Gradle inside it, against your mounted repository. Mount a named volume
#    for the Gradle cache too, so dependency downloads survive between runs.
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
1. **Init-Script Extraction (primary path):** An init script hooks the target project's own Gradle evaluation and emits its exact AGP version, `compileSdk`, `buildToolsVersion`, `ndkVersion`, and toolchain/bytecode-target facts straight from the evaluated model — not a text-based guess.
2. **Version Catalog Fallback:** If that doesn't yield a usable AGP version, it falls back to parsing `gradle/libs.versions.toml` directly.
3. **JDK Resolution:** The required JDK is derived from what the project declares — a Gradle daemon JVM criteria pin, toolchain declarations, bytecode targets, or the AGP minimum — never from whichever JVM happens to be running Gradlezilla itself.
4. **Environment Generation:** These requirements are synthesized into a dynamic `sdkmanager` bash command that installs only what your project strictly requires — nothing more, nothing less.

Running a real Gradle build sounds like it should be slow and flaky — daemon crashes, cache collisions, output that depends on whatever else is running on your machine. Gradlezilla avoids that by giving every invocation its own isolated Gradle user home and project cache directory, pinning the JDK explicitly instead of trusting the ambient one, and disabling Gradle's configuration cache for the extraction run so a stale cache entry can never silently skip extraction (see "Known Limitations" below).

**Determinism contract:** the same repository commit, extracted with the same Gradlezilla
version, produces an identical spec on the same machine, run after run. Across different
machines, the spec is identical once you exclude `extractionMetadata` — that block records
machine-specific facts (Gradle user home path, project cache dir, resolved daemon JDK path) by
design, so it's expected to differ from one machine to the next even when everything else about
the extraction agrees.

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

Gradlezilla has been tested against Gradle 8.0 through 9.7 — the range actually exercised by
the test suite (`InitScriptGradleVersionCompatTest` runs the packaged `extractor.gradle` against
a real Gradle 8.0 distribution) and the Repository Matrix Test results above.
`GradleJdkCompatibility`'s compatibility table assumes a floor of Gradle 5.0, but that floor is
untested — treat anything below 8.0 as unverified, not supported.

* **Floating base image tag:** the generated Dockerfile is pinned to `eclipse-temurin:<jdk>-jdk-jammy`,
  a tag that receives rolling updates — not a digest. Two builds on different days can pull a
  different underlying image even with an identical generated Dockerfile.
* **Unpinnable `platform-tools`:** the generated `sdkmanager` invocation installs `platform-tools`
  with no version pin (there's no per-project signal to pin it to), so it always resolves to
  whatever is latest at build time.
* **`local.properties` is visible through the mount:** since your repository is bind-mounted
  rather than copied, a host-checked-out `local.properties` with its own `sdk.dir` is visible
  inside the container too, pointing at a path that only exists on your host. AGP prefers
  `sdk.dir` over `ANDROID_HOME` when both are present, so a stale `local.properties` can break
  the build inside the container — delete it from the mount or don't check it in.
* **Daemon memory settings aren't baked in:** `kotlin.daemon.jvmargs`, `org.gradle.jvmargs`, and
  similar settings typically live in your own `~/.gradle/gradle.properties`, not the project, so
  the generated image has no opinion on them. Pass them with `-P`, or mount your
  `gradle.properties` into the container's Gradle user home.
* **Root-owned outputs on Linux:** the generated image has no `USER` instruction, so `docker run`
  executes as root by default — files Gradle writes back into your bind-mounted repository (build
  outputs, `.gradle/`) end up root-owned on the host. Run with `--user "$(id -u):$(id -g)"` and
  point `GRADLE_USER_HOME` at a directory that UID can write to.
* **Don't share one Gradle cache volume between concurrent containers:** a shared
  `GRADLE_USER_HOME` (e.g. a single named volume mounted by multiple containers at once) can hit
  Gradle's own cache locking across independent daemons. Give concurrent builds their own volume.
* **Windows JDK discovery is best-effort:** unlike macOS (`/usr/libexec/java_home -V`) or Linux
  (`/usr/lib/jvm`, `/usr/java`, `/opt`), Windows has no equivalent system-wide JDK registry, so
  discovery only checks `C:\Program Files\Java`, `C:\Program Files\Eclipse Adoptium`, and
  `%LOCALAPPDATA%\Programs`. A JDK installed somewhere else won't be found automatically — pass
  it explicitly with `--daemon-jdk`.

## 🤝 Contributing

Pull requests are welcome! If Gradlezilla fails to parse a specific repository structure, please open an issue with a link to the public repo or a snippet of the `build.gradle` file.

1. Clone the repository.
2. Build the CLI locally: `./gradlew :cli:installDist`
3. Run your local build: `./cli/build/install/gradlezilla/bin/gradlezilla --help`

## 📄 License

Gradlezilla is released under the [MIT License](LICENSE).