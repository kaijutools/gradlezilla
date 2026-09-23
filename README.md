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

* **Layered Builds:** Generate a multi-layer Dockerfile that resolves Gradle dependencies in a cacheable layer separate from your application source, so source-only edits don't invalidate the dependency layer:
  ```bash
  gradlezilla generate . --layered
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

## 🏗️ How to Test Your Generated Dockerfile

Once generated, you can test the build environment locally to ensure it successfully compiles your APK:

```bash
# 1. Build the immutable container environment
docker build -t my-android-app-builder .

# 2. Run the container to compile the app (e.g., Debug variant)
docker run --name app-builder my-android-app-builder bash -c "./gradlew assembleDebug --no-daemon"

# 3. Extract the finished APK back to your host machine
docker cp app-builder:/workspace/app/build/outputs/apk/debug ./extracted-apks

# 4. Clean up
docker rm app-builder
```

## 🧠 How it Works (Under the Hood)

Parsing Gradle files with Regex (or hand-rolled AST parsing) is a fool's errand due to the complexity of the Kotlin DSL and version catalogs. So instead of re-implementing Gradle's own evaluation logic, Gradlezilla asks Gradle itself — via the [Tooling API](https://docs.gradle.org/current/userguide/tooling_api.html), the same mechanism Android Studio uses to sync a project.

Gradlezilla uses a **Chain of Responsibility** of extractors:
1. **Init-Script Extraction (primary path):** An init script hooks the target project's own Gradle evaluation and emits its exact AGP version, `compileSdk`, `buildToolsVersion`, `ndkVersion`, and toolchain/bytecode-target facts straight from the evaluated model — not a text-based guess.
2. **Version Catalog Fallback:** If that doesn't yield a usable AGP version, it falls back to parsing `gradle/libs.versions.toml` directly.
3. **JDK Resolution:** The required JDK is derived from what the project declares — a Gradle daemon JVM criteria pin, toolchain declarations, bytecode targets, or the AGP minimum — never from whichever JVM happens to be running Gradlezilla itself.
4. **Environment Generation:** These requirements are synthesized into a dynamic `sdkmanager` bash command that installs only what your project strictly requires — nothing more, nothing less.

Running a real Gradle build sounds like it should be slow and flaky — daemon crashes, cache collisions, output that depends on whatever else is running on your machine. Gradlezilla avoids that by giving every invocation its own isolated Gradle user home and project cache directory, pinning the JDK explicitly instead of trusting the ambient one, and busting Gradle's configuration cache with a fresh token on every run so a stale cache entry can never silently skip extraction (see "Known Limitations" below).

## 📊 Matrix Test Status

Results from the latest [Repository Matrix Test](.github/workflows/matrix-test.yaml) run on `main`.

<!-- MATRIX-TABLE:START -->
Last verified at commit [`ccbbc6e`](https://github.com/kaijutools/gradlezilla/commit/ccbbc6ef5ae76641e247f7ce67a348af61d2844e).

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

## 🤝 Contributing

Pull requests are welcome! If Gradlezilla fails to parse a specific repository structure, please open an issue with a link to the public repo or a snippet of the `build.gradle` file.

1. Clone the repository.
2. Build the CLI locally: `./gradlew :cli:installDist`
3. Run your local build: `./cli/build/install/gradlezilla/bin/gradlezilla --help`

## 📄 License

Gradlezilla is released under the [MIT License](LICENSE).