#!/usr/bin/env bash
set -euo pipefail

TYPE="${1:-patch}"
FILE="$(cd "$(dirname "$0")/.." && pwd)/gradle.properties"

current_name=$(grep '^VERSION_NAME=' "$FILE" | cut -d'=' -f2)
current_code=$(grep '^VERSION_CODE=' "$FILE" | cut -d'=' -f2)

IFS='.' read -r major minor patch <<< "$current_name"

case "$TYPE" in
  patch)
    patch=$((patch + 1))
    ;;
  minor)
    minor=$((minor + 1))
    patch=0
    ;;
  major)
    major=$((major + 1))
    minor=0
    patch=0
    ;;
  *)
    echo "Usage: ./scripts/bump_version.sh [patch|minor|major]" >&2
    exit 1
    ;;
esac

next_name="$major.$minor.$patch"
next_code=$((current_code + 1))

sed -i.bak "s/^VERSION_NAME=.*/VERSION_NAME=$next_name/" "$FILE"
sed -i.bak "s/^VERSION_CODE=.*/VERSION_CODE=$next_code/" "$FILE"
rm -f "$FILE.bak"

echo "Updated VERSION_NAME: $current_name -> $next_name"
echo "Updated VERSION_CODE: $current_code -> $next_code"
