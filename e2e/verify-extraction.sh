#!/usr/bin/env bash
# Verifies one matrix-test.yaml repo run against ground truth and real-world-condition
# invariants. Unlike the old matrix test (which only checked gradlezilla's exit code), this
# parses --format json output, diffs two runs for determinism, checks the JSON's field names
# against a checked-in schema snapshot, compares every extracted field against a checked-in
# ground-truth file, asserts the generated Dockerfile never bakes in project content, and
# asserts extraction stored no configuration-cache entry — neither in the target repo's own
# .gradle nor in gradlezilla's isolated project-cache dir.
#
# Usage:
#   e2e/verify-extraction.sh <repoName> <run1.json> <run2.json> <expected.json> <schema.json> \
#       <targetRepoDir> <ccBeforeManifest> <extractionTimeSeconds> <reportMdOut> \
#       <projectCacheEntriesAfterRun1> <projectCacheEntriesAfterRun2>
#
# Exits 0 iff every check passed; always writes the Markdown report first, regardless of outcome.
set -uo pipefail

REPO_NAME="$1"
RUN1="$2"
RUN2="$3"
EXPECTED="$4"
SCHEMA="$5"
TARGET_REPO_DIR="$6"
CC_BEFORE="$7"
EXTRACTION_TIME_S="$8"
REPORT_OUT="$9"
PC_COUNT_1="${10}"
PC_COUNT_2="${11}"

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

overall_ok=true
rows=()      # rendered Markdown table rows for the per-field ground-truth section
checks=()    # rendered Markdown list items for the structural checks section

record_check() {
    local ok="$1" label="$2" detail="$3"
    if [[ "$ok" == "true" ]]; then
        checks+=("- ✅ $label")
    else
        checks+=("- ❌ $label — $detail")
        overall_ok=false
    fi
}

record_field() {
    local field="$1" expected="$2" actual="$3"
    local mark="✅"
    if [[ "$expected" != "$actual" ]]; then
        mark="❌"
        overall_ok=false
    fi
    rows+=("| $field | \`$expected\` | \`$actual\` | $mark |")
}

# --- 1. Both runs parse as JSON ---
run1_valid=true
run2_valid=true
jq empty "$RUN1" >/dev/null 2>&1 || run1_valid=false
jq empty "$RUN2" >/dev/null 2>&1 || run2_valid=false
record_check "$run1_valid" "run 1 is valid \`--format json\`" "$(head -c 300 "$RUN1" 2>/dev/null)"
record_check "$run2_valid" "run 2 is valid \`--format json\`" "$(head -c 300 "$RUN2" 2>/dev/null)"

if [[ "$run1_valid" != "true" ]]; then
    {
        echo "### $REPO_NAME — ❌ FAILED"
        echo
        printf '%s\n' "${checks[@]}"
    } >"$REPORT_OUT"
    exit 1
fi

# --- 2. Determinism: two runs, normalized, must be byte-identical ---
# gradleUserHome/projectCacheDir/daemonJavaHome/daemonJdkSource are absolute paths and a JDK
# source label that legitimately vary by machine/JDK layout even across two runs on the same
# runner in some configurations — normalize them out, same as e2e/determinism.sh's cross-JDK check.
NORMALIZE='.spec.extractionMetadata.gradleUserHome = "NORMALIZED"
  | .spec.extractionMetadata.projectCacheDir = "NORMALIZED"
  | .spec.extractionMetadata.daemonJavaHome = "NORMALIZED"
  | .spec.extractionMetadata.daemonJdkSource = "NORMALIZED"'

if [[ "$run2_valid" == "true" ]]; then
    jq -S "$NORMALIZE" "$RUN1" >"$WORK_DIR/run1.norm.json" 2>/dev/null
    jq -S "$NORMALIZE" "$RUN2" >"$WORK_DIR/run2.norm.json" 2>/dev/null
    if diff -q "$WORK_DIR/run1.norm.json" "$WORK_DIR/run2.norm.json" >/dev/null 2>&1; then
        record_check "true" "extraction is deterministic across 2 runs" ""
    else
        detail=$(diff "$WORK_DIR/run1.norm.json" "$WORK_DIR/run2.norm.json" 2>&1 | head -c 500)
        record_check "false" "extraction is deterministic across 2 runs" "$detail"
    fi
else
    record_check "false" "extraction is deterministic across 2 runs" "run 2 did not produce valid JSON"
fi

# --- 3. Schema guard: every key present must be a known (required or optional) field, and
# every required field (one with no Kotlin default, or jdkVersion's @EncodeDefault(ALWAYS))
# must be present. kotlinx.serialization omits any field equal to its Kotlin default, so a
# repo legitimately not having e.g. androidNdkVersion is not a failure — an UNKNOWN key
# (a rename) or a MISSING required key (e.g. jdkVersion silently regaining a default and
# vanishing) is.
schema_check() {
    local label="$1" actual_keys_jq="$2" schema_key="$3"
    local actual required known unknown missing
    actual=$(jq -cS "$actual_keys_jq" "$RUN1" 2>/dev/null)
    required=$(jq -cS ".\"$schema_key\".required | keys" "$SCHEMA" 2>/dev/null)
    known=$(jq -cS "(.\"$schema_key\".required | keys) + (.\"$schema_key\".optional | keys)" "$SCHEMA" 2>/dev/null)
    unknown=$(jq -cn --argjson actual "$actual" --argjson known "$known" '$actual - $known' 2>/dev/null)
    missing=$(jq -cn --argjson actual "$actual" --argjson required "$required" '$required - $actual' 2>/dev/null)
    if [[ "$unknown" == "[]" && "$missing" == "[]" ]]; then
        record_check "true" "schema: $label field names match snapshot" ""
    else
        record_check "false" "schema: $label field names match snapshot" "unknown=$unknown missing=$missing"
    fi
}

schema_check "envelope" '[keys[]]' "envelope"
schema_check "AndroidProjectSpec" '[.spec|keys[]]' "AndroidProjectSpec"
schema_check "ExtractionMetadata" '[.spec.extractionMetadata|keys[]]' "ExtractionMetadata"
if [[ "$(jq '.spec.modules | length' "$RUN1" 2>/dev/null)" -gt 0 ]]; then
    schema_check "ModuleSpec" '[.spec.modules[0]|keys[]]' "ModuleSpec"
fi

# --- 4. Ground truth: every extracted field must match the pinned-commit expectation ---
gt() { jq -c "$1" "$EXPECTED" 2>/dev/null; }
sp() { jq -c "$1" "$RUN1" 2>/dev/null; }

record_field "gradleVersion" "$(gt '.groundTruth.gradleVersion.value')" "$(sp '.spec.gradleVersion')"
record_field "jdkVersion" "$(gt '.groundTruth.jdkVersion.value')" "$(sp '.spec.jdkVersion')"
record_field "androidSdkVersion" "$(gt '.groundTruth.androidSdkVersion.value')" "$(sp '.spec.androidSdkVersion')"
record_field "androidBuildToolsVersion" "$(gt '.groundTruth.androidBuildToolsVersion.value')" "$(sp '.spec.androidBuildToolsVersion')"
record_field "moduleCount" "$(gt '.groundTruth.moduleCount.value')" "$(sp '.spec.modules | length')"
record_field "ndkPresent" "$(gt '.groundTruth.ndkPresent.value')" "$(sp 'if .spec.androidNdkVersion == null then false else true end')"
record_field "androidNdkVersion" "$(gt '.groundTruth.androidNdkVersion.value')" "$(sp '.spec.androidNdkVersion')"
record_field "androidCmakeVersion" "$(gt '.groundTruth.androidCmakeVersion.value')" "$(sp '.spec.androidCmakeVersion')"

# --- 5. Generated Dockerfile must never COPY/ADD project content (build-environment model) ---
dockerfile_bad_lines=$(jq -r '.dockerfile' "$RUN1" 2>/dev/null | grep -nE '^[[:space:]]*(COPY|ADD)\b' || true)
if [[ -z "$dockerfile_bad_lines" ]]; then
    record_check "true" "generated Dockerfile has no COPY/ADD of project content" ""
else
    record_check "false" "generated Dockerfile has no COPY/ADD of project content" "$(echo "$dockerfile_bad_lines" | tr '\n' ' ')"
fi

# --- 6. Extraction must never write into the target repo's own .gradle/configuration-cache ---
CC_DIR="$TARGET_REPO_DIR/.gradle/configuration-cache"
if [[ -d "$CC_DIR" ]]; then
    (cd "$TARGET_REPO_DIR" && find .gradle/configuration-cache -type f -exec sha256sum {} \;) | sort >"$WORK_DIR/cc-after.sha256"
else
    : >"$WORK_DIR/cc-after.sha256"
fi
if diff -q "$CC_BEFORE" "$WORK_DIR/cc-after.sha256" >/dev/null 2>&1; then
    record_check "true" "target repo's .gradle/configuration-cache untouched by extraction" ""
else
    detail=$(diff "$CC_BEFORE" "$WORK_DIR/cc-after.sha256" 2>&1 | head -c 500)
    record_check "false" "target repo's .gradle/configuration-cache untouched by extraction" "$detail"
fi

# --- 7. Extraction must not store a configuration-cache entry in gradlezilla's own project-cache
# dir either. That dir is persistent and isolated, so the pre-#39 cache-bust token grew it by one
# entry per invocation; with the configuration cache disabled outright nothing is written, and the
# entry count must be identical after run 1 and run 2.
if [[ "$PC_COUNT_2" -le "$PC_COUNT_1" ]]; then
    record_check "true" "gradlezilla project-cache dir did not grow across the 2 runs ($PC_COUNT_1 -> $PC_COUNT_2 entries)" ""
else
    record_check "false" "gradlezilla project-cache dir did not grow across the 2 runs" \
        "grew from $PC_COUNT_1 to $PC_COUNT_2 entries — extraction is still storing state per run"
fi

# --- Report ---
{
    if [[ "$overall_ok" == "true" ]]; then
        echo "### $REPO_NAME — ✅ PASSED (${EXTRACTION_TIME_S}s)"
    else
        echo "### $REPO_NAME — ❌ FAILED (${EXTRACTION_TIME_S}s)"
    fi
    echo
    echo "| Field | Expected | Actual | |"
    echo "|---|---|---|---|"
    printf '%s\n' "${rows[@]}"
    echo
    printf '%s\n' "${checks[@]}"
} >"$REPORT_OUT"

[[ "$overall_ok" == "true" ]]
