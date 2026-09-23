#!/usr/bin/env bash
# Proves gradlezilla's output is deterministic even with ambient Gradle daemons
# (other JDKs/Gradle versions, spawned by Android Studio or other projects) already
# running on the machine, and that stdout carries nothing but --format json output.
#
# Usage:
#   e2e/determinism.sh [projectDir ...]
#
# With no arguments, clones a small default corpus. To run the full OSS corpus:
#   GRADLEZILLA_E2E_REPOS='
#   nowinandroid https://github.com/android/nowinandroid.git
#   Signal-Android https://github.com/signalapp/Signal-Android.git
#   Tivi https://github.com/chrisbanes/tivi.git
#   DuckDuckGo-Android https://github.com/duckduckgo/Android.git
#   Thunderbird https://github.com/thunderbird/thunderbird-android.git
#   ' e2e/determinism.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="$(mktemp -d /tmp/gradlezilla-determinism.XXXXXX)"
GRADLEZILLA_BIN="$REPO_ROOT/cli/build/install/gradlezilla/bin/gradlezilla"

# Isolate this run's cache from the machine's real ~/.gradlezilla state so the test
# always starts cold and is reproducible.
export GRADLEZILLA_GRADLE_HOME="$WORK_DIR/gradlezilla-home"

cleanup() {
    # Daemons spawned against WORK_DIR (gradlezilla's own, plus the ambient one, both pointed
    # at a GRADLE_USER_HOME under WORK_DIR) outlive this script until their idle timeout,
    # holding lock files open — kill them first so rm can win.
    pkill -f "$WORK_DIR" 2>/dev/null || true
    sleep 1
    rm -rf "$WORK_DIR" 2>/dev/null || true
}
trap cleanup EXIT

log() { echo "[determinism] $*" >&2; }

DEFAULT_REPOS='
Sunflower https://github.com/android/sunflower.git
Timber https://github.com/JakeWharton/timber.git
'

build_cli() {
    log "Building gradlezilla CLI ..."
    (cd "$REPO_ROOT" && ./gradlew :cli:installDist --console=plain) >&2
}

# Starts an ambient daemon under a JDK different from the one running this script, in its
# own Gradle user home under WORK_DIR (kept separate from GRADLEZILLA_GRADLE_HOME) —
# simulating a daemon Android Studio or another project already left running.
start_ambient_daemon() {
    local ambient_jdk="$1"
    if [[ -z "$ambient_jdk" ]]; then
        log "No alternate JDK found via /usr/libexec/java_home; skipping ambient daemon setup."
        return
    fi

    local ambient_project="$WORK_DIR/ambient-project"
    mkdir -p "$ambient_project"
    cp -r "$REPO_ROOT/gradle" "$ambient_project/gradle"
    cp "$REPO_ROOT/gradlew" "$ambient_project/gradlew"
    cp "$REPO_ROOT/gradlew.bat" "$ambient_project/gradlew.bat" 2>/dev/null || true
    chmod +x "$ambient_project/gradlew"
    echo 'rootProject.name = "ambient"' >"$ambient_project/settings.gradle.kts"

    log "Starting ambient daemon under JAVA_HOME=$ambient_jdk ..."
    (
        cd "$ambient_project" &&
            JAVA_HOME="$ambient_jdk" GRADLE_USER_HOME="$WORK_DIR/ambient-gradle-home" \
                ./gradlew help --console=plain
    ) >&2
}

# The JDK gradlezilla itself will run under — respects JAVA_HOME like the JVM launcher does,
# rather than trusting `java` on PATH (which on macOS is often a /usr/bin/java dispatcher,
# not the real JDK home).
current_java_home() {
    local java_bin="java"
    [[ -n "${JAVA_HOME:-}" ]] && java_bin="$JAVA_HOME/bin/java"
    "$java_bin" -XshowSettings:properties -version 2>&1 |
        awk -F' = ' '/^ *java\.home/ {print $2}'
}

pick_ambient_jdk() {
    local running_home
    running_home="$(current_java_home)"
    if [[ -z "$running_home" ]] || ! command -v /usr/libexec/java_home >/dev/null 2>&1; then
        echo ""
        return
    fi
    /usr/libexec/java_home -V 2>&1 |
        grep -oE '/Library/Java/JavaVirtualMachines/[^ ]+/Contents/Home' |
        grep -v -F "$running_home" |
        head -1 || true
}

# Unlike the ambient daemon (any other JDK works — it never builds the target repo, it just
# proves daemon isolation), the JDK used to actually relaunch gradlezilla against a repo must
# itself be one that repo's own Gradle wrapper supports, or JdkPreflight correctly refuses it —
# a legitimate rejection, not a determinism failure, but not what this check is testing either.
# Prefer JDK 17, broadly supported by any actively maintained Gradle version; fall back to 21.
pick_cross_check_jdk() {
    local running_home candidate
    running_home="$(current_java_home)"
    if [[ -z "$running_home" ]] || ! command -v /usr/libexec/java_home >/dev/null 2>&1; then
        echo ""
        return
    fi
    for version in 17 21; do
        candidate="$(/usr/libexec/java_home -v "$version" 2>/dev/null || true)"
        if [[ -n "$candidate" && "$candidate" != "$running_home" ]]; then
            echo "$candidate"
            return
        fi
    done
    echo ""
}

resolve_repo_dir() {
    local name="$1" url="$2" dir="$WORK_DIR/repos/$name"
    if [[ -d "$dir/.git" ]]; then
        echo "$dir"
        return
    fi
    log "Cloning $name ..." >&2
    mkdir -p "$WORK_DIR/repos"
    git clone --depth 1 --quiet "$url" "$dir" >&2
    echo "$dir"
}

run_three_times() {
    local project_dir="$1" out_prefix="$2"
    local i
    for i in 1 2 3; do
        if ! "$GRADLEZILLA_BIN" generate "$project_dir" --dry-run --format json \
            >"${out_prefix}.run${i}.json" 2>"${out_prefix}.run${i}.stderr"; then
            return 1
        fi
    done
}

# Regression check for the bug where jdkVersion was derived from whichever JVM launched the
# CLI rather than from the project's own config: re-runs gradlezilla under a JDK different
# from run1's, and asserts the spec (aside from the daemonJavaHome/daemonJdkSource diagnostics,
# which are expected to differ — this deliberately sets JAVA_HOME, which daemon JDK discovery
# now honors as an explicit override) and generated Dockerfile are byte-identical either way.
check_cross_jdk() {
    local project_dir="$1" out_prefix="$2"

    if [[ -z "$CROSS_CHECK_JDK" ]]; then
        log "SKIP: no alternate JDK available for the cross-launcher-JDK check."
        return 0
    fi

    if ! JAVA_HOME="$CROSS_CHECK_JDK" "$GRADLEZILLA_BIN" generate "$project_dir" --dry-run --format json \
        >"${out_prefix}.altjdk.json" 2>"${out_prefix}.altjdk.stderr"; then
        log "FAIL: gradlezilla exited non-zero when launched under an alternate JDK — see ${out_prefix}.altjdk.stderr"
        return 1
    fi

    local normalize='del(.spec.extractionMetadata.daemonJavaHome, .spec.extractionMetadata.daemonJdkSource)'
    if ! jq "$normalize" "${out_prefix}.run1.json" >"${out_prefix}.run1.normalized.json" ||
        ! jq "$normalize" "${out_prefix}.altjdk.json" >"${out_prefix}.altjdk.normalized.json"; then
        log "FAIL: could not normalize JSON for the cross-launcher-JDK comparison"
        return 1
    fi

    if ! diff -q "${out_prefix}.run1.normalized.json" "${out_prefix}.altjdk.normalized.json" >/dev/null; then
        log "FAIL: spec/dockerfile differ when gradlezilla is launched under a different JDK"
        diff "${out_prefix}.run1.normalized.json" "${out_prefix}.altjdk.normalized.json" >&2 || true
        return 1
    fi
}

check_repo() {
    local name="$1" project_dir="$2"
    local out_prefix="$WORK_DIR/out-$name"

    log "Running gradlezilla against $name three times ..."
    if ! run_three_times "$project_dir" "$out_prefix"; then
        log "FAIL ($name): gradlezilla exited non-zero — see ${out_prefix}.run*.stderr"
        return 1
    fi

    if ! jq . "${out_prefix}.run1.json" >/dev/null 2>&1; then
        log "FAIL ($name): --format json output did not parse as JSON"
        log "$(head -c 300 "${out_prefix}.run1.json")"
        return 1
    fi

    if ! diff -q "${out_prefix}.run1.json" "${out_prefix}.run2.json" >/dev/null ||
        ! diff -q "${out_prefix}.run1.json" "${out_prefix}.run3.json" >/dev/null; then
        log "FAIL ($name): output differs across runs"
        diff "${out_prefix}.run1.json" "${out_prefix}.run2.json" >&2 || true
        diff "${out_prefix}.run1.json" "${out_prefix}.run3.json" >&2 || true
        return 1
    fi

    if ! check_cross_jdk "$project_dir" "$out_prefix"; then
        log "FAIL ($name): cross-launcher-JDK check failed (see above)"
        return 1
    fi

    log "PASS ($name): identical, valid JSON across 3 runs and across launcher JDKs"
}

main() {
    build_cli
    AMBIENT_JDK="$(pick_ambient_jdk)"
    start_ambient_daemon "$AMBIENT_JDK"
    CROSS_CHECK_JDK="$(pick_cross_check_jdk)"

    local failures=0

    if [[ $# -gt 0 ]]; then
        for project_dir in "$@"; do
            check_repo "$(basename "$project_dir")" "$project_dir" || failures=$((failures + 1))
        done
    else
        local repos="${GRADLEZILLA_E2E_REPOS:-$DEFAULT_REPOS}"
        while read -r name url; do
            [[ -z "$name" ]] && continue
            local dir
            dir="$(resolve_repo_dir "$name" "$url")"
            check_repo "$name" "$dir" || failures=$((failures + 1))
        done <<<"$repos"
    fi

    if [[ "$failures" -gt 0 ]]; then
        log "$failures repo(s) failed the determinism check."
        exit 1
    fi
    log "All repos produced identical, valid JSON across 3 runs and across launcher JDKs."
}

main "$@"
