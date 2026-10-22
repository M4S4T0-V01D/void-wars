#!/usr/bin/env bash
# Builds Void Wars gently (niced), without writing to ~/.gradle or ~/.android. Dependencies come from the
# machine's shared Gradle cache; anything missing there is fetched into .gradle-home.
# Pass --offline to stay off the network. Usage: ./build.sh [assembleRelease|assembleDebug|testDebugUnitTest|...]
set -euo pipefail
cd "$(dirname "$0")"
G=$(ls -d "$HOME"/.gradle/wrapper/dists/gradle-8.11.1-bin/*/gradle-8.11.1 | head -1)
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=${TMPDIR:-/tmp}"
export ANDROID_USER_HOME="$PWD/.android-home"
export GRADLE_USER_HOME="$PWD/.gradle-home"
export GRADLE_RO_DEP_CACHE="$HOME/.gradle/caches"
args=("$@")
[ ${#args[@]} -eq 0 ] && args=(assembleRelease)
nice -n 19 "$G/bin/gradle" --no-daemon -Pkotlin.compiler.execution.strategy=in-process "${args[@]}"
# A finished release APK: check its signature (Void Store refuses one signed with another key), copy it next to the project.
apk=app/build/outputs/apk/release/app-release.apk
if [[ " ${args[*]} " == *" assembleRelease "* && -f $apk ]]; then
    "$(ls -d /opt/android-sdk/build-tools/* | sort -V | tail -1)/apksigner" verify --min-sdk-version 33 "$apk" 2>/dev/null
    cp "$apk" VoidWars.apk
    echo "VoidWars.apk ($(du -h VoidWars.apk | cut -f1)), signature verified"
fi
