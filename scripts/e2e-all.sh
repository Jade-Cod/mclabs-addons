#!/usr/bin/env bash
# Runs the unit tests and the in-game E2E suite on every Minecraft version at once.
#
#   scripts/e2e-all.sh              # all three, in parallel
#   scripts/e2e-all.sh pit          # only scenarios whose name contains "pit"
#
# A full run then runs PerformanceGameTest on each version, one at a time: its budgets are
# microseconds a frame, which three clients sharing the machine would blow on their own.
#
# Scenarios and goldens are authored here, on the 1.21.11 checkout, and copied into
# the 26.x worktrees before each run, so every version is held to the same result.
# Point E2E_262 / E2E_263 elsewhere if those worktrees live somewhere else.
set -u
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT="$ROOT/build/e2e-all"
ONLY=${1:-}
VERSIONS=("1.21.11=$ROOT" "26.2=${E2E_262:-$ROOT/.claude/worktrees/e2e-26.2}" "26.3=${E2E_263:-$ROOT/.claude/worktrees/e2e-26.3}")

rm -rf "$OUT" && mkdir -p "$OUT"
pids=()
for entry in "${VERSIONS[@]}"; do
	version=${entry%%=*} dir=${entry#*=}
	if [ ! -d "$dir" ]; then
		echo "$version: no checkout at $dir" | tee "$OUT/$version.log"
		continue
	fi
	if [ "$dir" != "$ROOT" ]; then
		rsync -a --delete "$ROOT/src/gametest/e2e/" "$dir/src/gametest/e2e/"
	fi
	(cd "$dir" && ./gradlew test runClientGameTest --console=plain ${ONLY:+-Pe2eOnly="$ONLY"} >"$OUT/$version.log" 2>&1
		echo $? >"$OUT/$version.exit"
		cp -f build/e2e/report.json "$OUT/$version.json" 2>/dev/null
		rm -rf "$OUT/$version-screenshots" && cp -R build/run/clientGameTest/screenshots "$OUT/$version-screenshots" 2>/dev/null) &
	pids+=($!)
done
wait "${pids[@]}"

if [ -z "$ONLY" ]; then
	for entry in "${VERSIONS[@]}"; do
		version=${entry%%=*} dir=${entry#*=}
		[ -d "$dir" ] || continue
		(cd "$dir" && ./gradlew runClientGameTest -Pperf --console=plain >"$OUT/$version-perf.log" 2>&1
			echo $? >"$OUT/$version-perf.exit")
	done
fi

python3 - "$OUT" <<'PY'
import json, os, sys
out = sys.argv[1]
worst = 0
for version in ("1.21.11", "26.2", "26.3"):
    exit_file = os.path.join(out, version + ".exit")
    code = open(exit_file).read().strip() if os.path.exists(exit_file) else "?"
    report = os.path.join(out, version + ".json")
    if not os.path.exists(report):
        print(f"{version:8} build failed before the suite ran (exit {code}) — see {version}.log")
        worst = 1
        continue
    results = json.load(open(report))
    counts = {}
    for r in results:
        counts[r["status"]] = counts.get(r["status"], 0) + 1
    print(f"{version:8} " + "  ".join(f"{k} {v}" for k, v in sorted(counts.items())) + f"  (exit {code})")
    for r in results:
        if r["status"] == "FAIL":
            print(f"         FAIL {r['scenario']}: {'; '.join(r['problems'][:2]) or 'state differs from expected'}")
    if code != "0":
        worst = 1
    perf_exit = os.path.join(out, version + "-perf.exit")
    if os.path.exists(perf_exit):
        lines = open(os.path.join(out, version + "-perf.log"), errors="replace").read().splitlines()
        hud = next((l.split("[perf] ", 1)[1] for l in lines if "[perf] HUD render pass" in l), "no HUD figures")
        over = [l.split("OVER BUDGET: ", 1)[1] for l in lines if "OVER BUDGET" in l]
        perf_code = open(perf_exit).read().strip()
        print(f"         perf {'PASS' if perf_code == '0' else 'FAIL'}: {hud}")
        for miss in over:
            print(f"         perf over budget: {miss}")
        if perf_code != "0":
            worst = 1
print(f"\nLogs, reports and screenshots: {out}")
sys.exit(worst)
PY
