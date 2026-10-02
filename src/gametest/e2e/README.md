# In-game end-to-end tests

The real client, the real mod, and MCLabs replayed from captures through a local world.

```bash
scripts/e2e-all.sh              # unit + in-game tests on 1.21.11, 26.2 and 26.3 at once
scripts/e2e-all.sh pit,bounty   # only scenarios whose names contain pit or bounty
./gradlew runClientGameTest     # this version only
./gradlew runClientGameTest -Pe2eUpdate   # rewrite every expected/*.json, then review the diff
./gradlew runClientGameTest -Pperf        # PerformanceGameTest alone: frame, chat, tick budgets and leaks
```

Results land in `build/e2e-all/` (one log, report and screenshot folder per version).
A full `e2e-all.sh` run then runs the performance test on each version in turn, since its
budgets are microseconds a frame and three clients at once would blow them. Add `-Pjfr` to
record a profile (`build/gametest.jfr`) when a budget fails.

## What a run checks

Each scenario is replayed in name order through one world whose sidebar reads
`MCLabs Spawn`, as player `Ophiliah`. After each one the runner records:

- **state**: every key of `state.json` / `runners.json` the scenario changed, with
  timestamps rewritten as `now+37m30s` (to the nearest 10 s)
- **clicks**: every slot click the mod sent to a replayed menu

and compares them with `expected/<scenario>.json`. Any `ERROR` line or stack trace logged
while it played also fails it. A scenario without an expected file writes one and reports
NEW, which means someone has to look at it. The same expected files are used on every
Minecraft version, so a port that behaves differently fails.

Scenarios share one world, so one can change what the next one sees. The order is fixed
by name, so results are repeatable. Adding a scenario can change a later one's expected
file, which is what `-Pe2eUpdate` plus a diff review is for.

Screenshots are taken at the end of every scenario, just before every menu closes, and
wherever a scenario asks for one. Those are what show the HUD and the casino and crate
boards.

## Adding one

`scripts/e2e-scenario.py` turns a gui-dumper capture, a Minecraft log or a component
dump into a scenario:

```bash
scripts/e2e-scenario.py ~/Downloads/mines.jsonl casino-mines --local
scripts/e2e-scenario.py latest.log bounty-hunt --grep 'Bounty »'
scripts/e2e-scenario.py message-24.txt police-prestige --title 'Prestige Progress'
```

`scenarios/` is committed to a public repo, so the script drops player chat from
anything written there. `--local` writes to the gitignored `local/` (and
`local-expected/`) and keeps everything: whole sessions and raw captures go there.

Records are gui-dumper's (`chat`, `actionbar`, `screen`, `full`, `delta`, `close`) plus
`sidebar`, `command` (server console), `playerCommand`, `key`, `mouse`, `window`,
`wait`, `screenshot` and `note`. `Replay.java` is the only file that differs between
version branches.

## Known gaps

- gui-dumper records an item's id, count, name and lore only. Anything a reader takes from
  a player head's texture (the crate odds pages' spent/ahead heads) or custom model data
  (rental coupons) cannot be replayed until the dumper writes those too.
- Entity-driven tracking (the bite marker, Mastery kills, Raid Mine holograms) and
  inventory-driven tracking (Mastery catches and sales) have no scenarios yet.
