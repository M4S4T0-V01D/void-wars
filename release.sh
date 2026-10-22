#!/usr/bin/env bash
# Releases Void Wars: bumps versionCode and the patch version, commits and pushes, then publishes the same signed
# APK twice: to Void Store (which builds it, runs the tests and checks it's newer and signed with the same key)
# and as a GitHub release with a latest.json beside it, which the app's Settings › Updates picks up.
# The APK is built and signed on this PC, so the signing key never leaves it.
# Usage: ./release.sh "What changed"      (./release.sh --current "Notes" publishes the current version unbumped)
set -euo pipefail
cd "$(dirname "$0")"
bump=1
if [ "${1:-}" = "--current" ]; then bump=0; shift; fi
notes=${1:?Usage: ./release.sh \"What changed\"}
git pull --ff-only
if [ $bump = 1 ]; then
    code=$(sed -n 's/^versionCode=//p' version.properties)
    name=$(sed -n 's/^versionName=//p' version.properties)
    newname="${name%.*}.$(( ${name##*.} + 1 ))"
    sed -i "s/^versionCode=.*/versionCode=$((code + 1))/; s/^versionName=.*/versionName=$newname/" version.properties
    git add -A
    git commit -m "$notes" -m "Void Wars $newname"
fi
git push
code=$(sed -n 's/^versionCode=//p' version.properties)
name=$(sed -n 's/^versionName=//p' version.properties)

node ../void-store/store.mjs publish wars --notes "$notes"
cp app/build/outputs/apk/release/app-release.apk VoidWars.apk

python3 - "$code" "$name" "$notes" <<'EOF'
import hashlib, json, sys
apk = open("VoidWars.apk", "rb").read()
json.dump({"versionCode": int(sys.argv[1]), "versionName": sys.argv[2], "sha256": hashlib.sha256(apk).hexdigest(),
           "size": len(apk), "notes": sys.argv[3]}, open("latest.json", "w"), indent=2)
EOF
gh release create "v$name" VoidWars.apk latest.json --title "Void Wars $name" --notes "$notes" --latest
rm latest.json
echo "Released Void Wars $name to Void Store and GitHub."
