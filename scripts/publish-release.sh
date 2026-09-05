#!/usr/bin/env bash
#
# Publishes one mod version to Modrinth and CurseForge for every Minecraft line
# we support.
#
# Each line lives on its own branch — they can't share a build, because 26.x
# uses Mojang names and 1.21.11 uses Yarn — but they publish under the same
# version number. The in-game update checker filters by game version, so a
# 1.21.11 player is never offered the 26.2 jar and vice versa. That only holds
# if the version numbers stay in lockstep, so this refuses to publish when they
# drift.
#
# Both stores go out together, so a release can never end up on one and not the
# other — a half-published version is worse than an unpublished one, because the
# in-game update checker starts offering a jar half the players cannot get.
#
# Usage:
#   MODRINTH_TOKEN="$(cat ~/.config/mclabs-addons/modrinth-token)" \
#   CURSEFORGE_TOKEN="$(cat ~/.config/mclabs-addons/curseforge-token)" \
#     scripts/publish-release.sh <changelog-file> [--dry-run]
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
	gradle_flags+=(-PmodrinthDebug -PcurseforgeDebug)
	# Minotaur's debugMode never touches the network, so a stand-in token is enough.
	export MODRINTH_TOKEN=${MODRINTH_TOKEN:-dry-run-no-upload}
	# CurseForgeGradle is different: even in debugMode it fetches the game-version
	# list from the API first, which 400s without real credentials. So a dry run
	# needs the real CurseForge token — it still uploads nothing.
	if [[ -z ${CURSEFORGE_TOKEN:-} ]]; then
		echo "error: CURSEFORGE_TOKEN is required even for --dry-run: the CurseForge" >&2
		echo "       plugin fetches game versions before it checks debug mode." >&2
		exit 78
	fi
	echo "== dry run: validating only, nothing will be uploaded =="
else
	# Check both up front. Finding out CurseForge is unauthenticated *after*
	# Modrinth has published is exactly the split this script exists to prevent.
	for var in MODRINTH_TOKEN CURSEFORGE_TOKEN; do
		if [[ -z ${!var:-} ]]; then
			echo "error: $var is not set (use --dry-run to validate without it)" >&2
			exit 78
		fi
	done
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
	# The ${a[@]+"${a[@]}"} form is deliberate: under `set -u`, bash 3.2 (what macOS
	# ships) treats an empty array expansion as an unbound variable. A dry run puts a
	# flag in the array and so never hits it — only a real publish does.
	(cd "${dirs[$i]}" && ./gradlew publishRelease -PchangelogFile="$changelog" ${gradle_flags[@]+"${gradle_flags[@]}"})
done

echo "== done: v${versions[0]} published to Modrinth and CurseForge for ${#BRANCHES[@]} Minecraft versions =="
