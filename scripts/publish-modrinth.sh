#!/usr/bin/env bash
#
# Publishes one mod version to Modrinth for every Minecraft line we support.
#
# Each line lives on its own branch — they can't share a build, because 26.x
# uses Mojang names and 1.21.11 uses Yarn — but they publish under the same
# version number. The in-game update checker filters by game version, so a
# 1.21.11 player is never offered the 26.2 jar and vice versa. That only holds
# if the version numbers stay in lockstep, so this refuses to publish when they
# drift.
#
# Usage:
#   MODRINTH_TOKEN="$(cat ~/.config/mclabs-addons/modrinth-token)" \
#     scripts/publish-modrinth.sh <changelog-file> [--dry-run]
#
# Builds happen in throwaway worktrees, so whatever is uncommitted in your
# working copy is neither published nor disturbed.

set -euo pipefail

BRANCHES=(main mc/26.2)

changelog=${1:-}
dry_run=${2:-}

if [[ -z $changelog ]]; then
	echo "usage: $0 <changelog-file> [--dry-run]" >&2
	exit 64
fi
if [[ ! -f $changelog ]]; then
	echo "error: changelog file not found: $changelog" >&2
	exit 66
fi
changelog=$(cd "$(dirname "$changelog")" && pwd)/$(basename "$changelog")

gradle_flags=()
if [[ $dry_run == "--dry-run" ]]; then
	gradle_flags+=(-PmodrinthDebug)
	# debugMode never uploads, but Minotaur still reads the token property, so
	# stand one in rather than making a dry run need real credentials.
	export MODRINTH_TOKEN=${MODRINTH_TOKEN:-dry-run-no-upload}
	echo "== dry run: validating only, nothing will be uploaded =="
elif [[ -z ${MODRINTH_TOKEN:-} ]]; then
	echo "error: MODRINTH_TOKEN is not set (use --dry-run to validate without it)" >&2
	exit 78
fi

repo=$(git rev-parse --show-toplevel)
cd "$repo"

for branch in "${BRANCHES[@]}"; do
	git rev-parse --verify --quiet "$branch" >/dev/null \
		|| { echo "error: no such branch: $branch" >&2; exit 65; }
done

staging=$(mktemp -d)
cleanup() {
	for dir in "$staging"/*/; do
		[[ -d $dir ]] && git worktree remove --force "$dir" 2>/dev/null || true
	done
	rm -rf "$staging"
}
trap cleanup EXIT

# Check every branch out first, so a version mismatch fails before any upload.
declare -a dirs=() versions=()
for branch in "${BRANCHES[@]}"; do
	dir=$staging/${branch//\//-}
	git worktree add --detach --quiet "$dir" "$branch"
	version=$(sed -n 's/^mod_version=//p' "$dir/gradle.properties")
	mc=$(sed -n 's/^minecraft_version=//p' "$dir/gradle.properties")
	[[ -n $version ]] || { echo "error: no mod_version in $branch" >&2; exit 65; }
	echo "  $branch -> v$version for Minecraft $mc"
	dirs+=("$dir")
	versions+=("$version")
done

for version in "${versions[@]}"; do
	if [[ $version != "${versions[0]}" ]]; then
		echo "error: version mismatch across branches: ${versions[*]}" >&2
		echo "       bump mod_version in gradle.properties so they match." >&2
		exit 65
	fi
done

for i in "${!dirs[@]}"; do
	echo "== publishing ${BRANCHES[$i]} =="
	(cd "${dirs[$i]}" && ./gradlew modrinth -PchangelogFile="$changelog" "${gradle_flags[@]}")
done

echo "== done: v${versions[0]} published for ${#BRANCHES[@]} Minecraft versions =="
