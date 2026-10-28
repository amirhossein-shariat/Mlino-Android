#!/usr/bin/env bash
# Runs only the pure-Kotlin core tests, without the Android plugin (useful where the Android SDK/plugin cannot be downloaded).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
W="$(mktemp -d)"
cat > "$W/settings.gradle.kts" <<'KTS'
pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "core-only"
KTS
cat > "$W/build.gradle.kts" <<'KTS'
plugins { kotlin("jvm") version "2.0.21" }
kotlin { jvmToolchain(17) }
dependencies { testImplementation(kotlin("test")) }
tasks.test { useJUnitPlatform(); testLogging { events("failed"); showStandardStreams = false } }
KTS
mkdir -p "$W/src"
ln -s "$ROOT/core/src/main" "$W/src/main"
ln -s "$ROOT/core/src/test" "$W/src/test"
cd "$W"
${GRADLE:-gradle} --no-daemon -q test
echo "core tests passed: $(grep -ho 'tests="[0-9]*"' build/test-results/test/*.xml | tr -dc '0-9\n' | paste -sd+ | bc)"
