# Changelog

## [1.16.0] - 2026-09-05

> **Downgrading to 1.15.x:** your old `config/labsaddons.json` is left in place
> untouched, so an older version still starts and reads it — but it is frozen at
> the moment you upgraded, so anything changed since (layout tweaks, runner jobs,
> tracked timers) will not be there. Nothing is lost from 1.16.0's own files; they
> are simply ignored by older versions.

### Added
- **HUD profiles.** Save more than one widget layout, name them, and switch between them, so the HUD can match what you are actually doing instead of being one compromise for everything. A profile remembers every widget's position, size, colours and whether it is switched on at all, plus the display choices inside them — your pinned progress rows, which ability cooldowns are hidden, which Raid Mine resources are shown, and whether cooldowns stack in a column. Everything else — bite marker colours, the item-uses overlay, the low-job alarm — stays shared, so switching layouts never quietly changes a setting. New profiles start as a copy of the one you are on, and can be renamed or deleted at any time.
- **Profiles can load themselves when you change worlds.** The server announces which world you have arrived in, so the mod reads that and swaps to whichever profile you bound to it — a fishing layout at Spawn, a mining one in the Underworld, and back again, with nothing to press. All five worlds worth binding a layout to are listed from the start: Spawn, the Overworld, the Underworld, Events and The Pit. Any world you leave unbound simply keeps whichever profile is already loaded. Manage all of it from the new **Profile** button in the HUD editor.
- **Sunken Treasure now shows up in the Bounties widget.** The Fishing Weekend hides a batch of barrels along Spawn's shorelines and announces the count as players find them, so the widget tracks it the same way it tracks a Bounty Hunt — a barrel row reading how many are still out there, sitting under the bounty chest row when both events are running. Each new wave re-seeds the count on its own, and the row goes away once the last barrel is claimed. Joined partway through a wave and missed the announcement? Open **`/fw`** once and the widget picks up whatever the menu says is still out there.
- **The HUD editor's Widgets list rolls up out of the way.** It sits in the top-left corner and there was no getting it off there, so anything you wanted to place behind it had to be nudged in blind. Click the **Widgets** header and the list rolls away to just its title bar — the same easing the Runner Leaderboard's job history uses — freeing the corner to click, drag and marquee in like any other part of the screen. Click it again to roll it back down; it remembers which way you left it.
- **The editor's panels ghost out while you move a widget.** Drag, resize or arrow-nudge anything and both the Widgets list and the settings panel fade back to a quarter of their opacity for as long as you're moving, so you can watch a widget travel underneath them instead of losing sight of it. Both come back the moment you let go. The bottom toolbar stays solid — it's the editor's frame rather than something in your way, and its nudge hint is worth reading precisely while you're nudging.
- **The low-job alarm's "Alert At" is now a slider.** It was a typing box, which meant it could hold any number you liked (including ones that would never fire) and was the one part of the settings panel that stayed a stark white box while everything around it faded. It now slides from 0 to 30 jobs with the figure on the control itself, so there is nothing to type, nothing to mistype, and one less row in the panel.

### Changed
- **The config is now a folder instead of one file.** `config/labsaddons.json` has become `config/labsaddons/`, holding your settings, the server state the mod tracks, the runner leaderboard and your profiles as separate files. The split is by how each kind of data behaves: settings change when you change them, tracked state changes constantly, and the leaderboard only grows. Keeping them apart means a save no longer rewrites all of it at once, and a file that somehow gets damaged costs you only what was in it rather than everything. **Your existing config is migrated automatically on first launch and left in place as a backup** — nothing to do, and nothing moves on screen.
- **The mod writes to disk far less.** Saves used to be whole-file and immediate, including from the per-tick paths that fire on every mob kill, fish caught and chem sold. They are now batched onto a background thread and skipped entirely when nothing has changed, so a busy fishing or Pit session stops hitting the disk hundreds of times. The leaderboard — much the largest part — is only rewritten when a runner job actually changes it.
- **"Reset All" is now "Reset Profile".** With more than one layout in play the old name was ambiguous, and it only ever reset the layout you were looking at anyway. It now also clears the things that travel with a layout — pinned progress rows, hidden ability cooldowns, hidden Raid Mine resources and the cooldown stacking direction — so a reset is a genuinely clean profile. Your other profiles, your tracked server state and your global settings are untouched.
- **The editor toolbar wraps instead of overlapping itself.** On a narrow screen (GUI scale 4 on a 1080p monitor, say) the buttons had no room for a full row; the Profile, Grid and Snap group now moves to a row of its own rather than running into the buttons on the left.

### Fixed
- **Fixed a crash opening the Runner Leaderboard on Minecraft 26.2.** The rotating 3D model beside the list is a display-only player that never joins a world, so it had never been given an entity id — which 26.2 refuses to hand out rather than defaulting, taking the game down as the screen drew. The models are given their own ids now.
- **Fixed a startup crash on Minecraft 26.2.** Restoring your saved Mastery board built its quest icons during start-up — but 26.2 binds item components later than it fills the item registry, so the game died with "Components not bound yet" before reaching the menu. Only players with a saved board hit it. The boards are now restored when you join a world, which is both safely after start-up and the first moment they matter. A quest icon that still cannot be built now just leaves the row without one rather than taking the client down.
- **Config writes can no longer be corrupted by a crash.** The old save truncated the file and then wrote it, so losing the game mid-write left a broken config. Each file is now written beside itself and moved into place, which either fully succeeds or leaves the previous version untouched.
- **Items you already had no longer count toward a Mastery challenge you just started.** Starting a `Catch` challenge while holding a stack of what it asks for — 13 fishing crates already in the bag — credited the whole stack the instant the challenge went live. Only what you catch after starting counts now. The bar was only wrong while a challenge was already active alongside the new one, which is the usual case, and reopening `/mastery` always corrected it.

## [1.15.1] - 2026-09-04

> **Updating from 1.15.0:** the in-game "update available" notice in 1.15.0 may
> send you to the wrong download. 1.15.1 is the first release published for more
> than one Minecraft version, and the old notice built its link from the version
> number alone, which no longer identifies a single file — so it can offer a jar
> built for a different Minecraft version than the one you are running, which will
> not load. **Please download this release manually from the Modrinth page and
> check the file matches your Minecraft version.** The notice is fixed in this
> release, so updating from 1.15.1 onwards works normally.

### Added
- **New Raid Mine HUD widget.** Tracks the Raid Mine's double mine drops buff, counting down the seconds you have left. Each proc stacks on whatever time is still running, so a fresh roll during an active buff extends it rather than replacing it. The duration is read from the message itself, so both the 15-second and 30-second rolls work. The widget only appears while the buff is up.
- **The Raid Mine widget now tracks what you gather.** Raid mine blocks drop no items — the server shows a short-lived hologram saying what you generated — so the widget reads those holograms and keeps a running total for the session, with a per-hour rate beside each resource. On screen each resource is shown as the server's own coloured symbol and its figures are compacted ("64k", "1.32m/h"), keeping the rows narrow enough to read at a glance — the colour is what tells the Flux and Essence tiers apart, since they share a letter. Full names (Energy, the Flux and Essence tiers of Value, Progress and Score, Siege Fuel, Company Gold and Raid Points) appear in the HUD editor, where there is room for them. A resource the server adds later still tracks, under its own symbol.
- **Choose which resources the widget shows.** Every resource seen this session gets its own on/off switch in the HUD editor's inspector, so you can watch only what you are actually farming.
- **The widget only appears during a raid**, and its totals are cleared by a Reset Session action in the HUD editor — nothing resets on its own.

### Fixed
- **The "update available" chat notice now links to the right download.** The link was built from the version number, which is about to stop being unique — the same version is published once per Minecraft version. Modrinth resolves such a link to only one of them, arbitrarily, so a player could be sent a jar built for a different Minecraft version than the one they are running. The notice now links by Modrinth's own version id, which identifies exactly one file.
- **Pit kills are only credited when you landed the last hit.** The Pit is shared, so mobs other players were fighting died in front of you and counted toward your `Kill <mob>` Mastery challenge. A kill now only counts if one of your own hits landed on that mob within the last two seconds.

## [1.15.0] - 2026-08-09

### Added
- **New Mastery & Prestige HUD widget.** One widget showing exp-style progress bars for both of MCLabs' long-run progress systems: your **5 active Mastery challenges** and your **14 chem prestige tracks**. Each row shows the challenge or chem's own icon, how far along it is, and the amount you just earned. Open **`/mastery`** once to sync your challenges and run **`/prestige progress`** once to sync your prestige — after that both update live as you play, with no need to reopen anything.
- **Pin the rows you care about.** By default a row only appears while it's gaining and then fades away, so the widget stays out of your way. Select the widget in the HUD editor and use **Keep On Screen** to pin any challenge or chem you want up permanently — useful for whatever you're actively grinding. Pinned rows hold their position instead of reshuffling as they move.
- **Mastery challenges advance live from four sources.** Selling chems (per-dealer and per-chem challenges alike), killing mobs in the Pit, catching fish, and winning chat reactions all move the bars the moment they happen, instead of waiting until you reopen `/mastery`. Mini-event placements and bounty completions count too. Reopening the GUI always re-syncs against the server, so the bars can only ever be behind, never wrong.
- **Chem prestige progress reads the server's exact figures.** The precise amount each sale earns is stated in the sell message's hover tooltip, and `/prestige progress` states each chem's exact total the same way — the mod reads both directly rather than trying to recalculate them, so the numbers always match the game. Finished chem tracks are recognised as complete and stop counting, including for players who have already finished all of their prestige.

### Fixed
- **Widget names no longer get cut off in the HUD editor.** The Widgets rail was a fixed width, so longer names were chopped mid-word ("Lab Wars Booste", "Personal Booste"). It now sizes itself to the longest name, and anything that still cannot fit ends in an ellipsis instead of simply stopping.

### Changed
- **Smuggler Satchel contents now count toward Mastery.** Selling to a dealer empties your satchel alongside your inventory, so the mod learns what the satchel holds — from opening it, or from the chat confirmation when you load it — and credits that share of the sale too.

## [1.14.1] - 2026-07-25

### Added
- **The "update available" chat notice is now clickable.** When a newer version is found on join, clicking anywhere on the line opens that release's Modrinth page in your browser, where the download button and its changelog are — no more hunting for the mod page yourself. Hovering shows "Click to download" first, and Minecraft still asks for confirmation before opening any link.

### Changed
- **Cloth Config is no longer required to install the mod.** It was only ever used to draw the Mod Menu settings screen, while the in-game HUD editor (`;`) is where everything is actually configured — so it is now an optional dependency. Without it the mod runs normally and Mod Menu just shows no settings button; install it if you want that screen.
- **The mod now runs on older Fabric Loader versions.** The requirement dropped from 0.19.2 to 0.17.3 (what Fabric API itself asks for) — nothing in the mod ever needed a newer loader, so anyone who hadn't updated theirs was being turned away for no reason.
- **The Minecraft version requirement is now exactly 1.21.11** instead of "1.21.11 or any later 1.21.x". The mod hooks into internals that move between versions, so on a future 1.21.12 you now get a clear "requires Minecraft 1.21.11" message at launch instead of a crash.

## [1.14.0] - 2026-07-09

### Added
- **New Runner Jobs HUD widget.** Tracks MCLabs Runner job postings straight from chat — posted, completed, and failed counts plus the total money earned this session — drawn as a new draggable HUD widget, with a Reset Session action in the HUD editor's inspector panel.
- **A low-jobs alarm for the Runner Jobs widget.** Turn it on in the HUD editor and pick a threshold and alert sound; the moment your posted jobs drop to that number, a **red on-screen title** appears with a white "N jobs left" subtitle and the alert tone plays **twice** so it's hard to miss. It fires once per dip (not repeatedly while low) and re-arms when you reset the session.
- **A Runner Leaderboard ranks the runners working your jobs.** The HUD Studio has a new **Stats** button that opens a full-screen table of everyone who's completed a job you posted — each row shows their **player head**, jobs done and failed, **Completion %**, **Avg Time**, and total value sold, ranked by jobs completed. **Hover** a runner to watch their character model run in place, turned toward the list, beside it, and **click** a runner to roll open a shutter right under their row with their most recent completed jobs — drug, value, time taken and date, ten to a page with `<` / `>` turners — pushing the rows below it down; click again (or another runner) to roll it back up. It fills in automatically from chat as jobs finish and persists across relogs (average time is a rough estimate); open `/supplier` any time to re-sync how many jobs you have posted. Clearing the board now asks for confirmation first, so an accidental click can't wipe it.

### Changed
- **The mod's internal id is now `labsaddons`, completing the rename to MCLabs Addons.** Nothing changes in how the mod looks or works — everything carries over automatically on first launch:
  - All settings — HUD layouts, colors, timers, and tracked state — move from `config/fishbite.json` to `config/labsaddons.json`. The old file is left in place as a backup.
  - Custom key bindings for **Open HUD Editor**, **Deposit Chemtainer**, and **Withdraw Chemtainer** are carried over from Minecraft's `options.txt`, so rebound keys stay exactly where you put them.
- **Remove the old jar when updating.** If a 1.13.x-or-older jar is still in your `mods` folder, the game now stops at launch with a clear incompatibility message instead of silently running two copies of the mod — delete the old jar and relaunch.
- The build now produces `mclabs-addons-<version>.jar` directly (previously it built as `fishbite-indicator-<version>.jar` and was renamed at release time).

### Fixed
- **Hardened item and runner parsing against malformed server data.** An item whose "Charges" lore held an absurdly large number could crash the client while drawing the remaining-uses overlay; it now ignores the value instead. A failed player-skin lookup on the runner screens no longer sticks on the default skin until restart — it retries. And the per-runner leaderboard is now capped so a stray or malicious server can't grow the config file without limit.

## [1.13.0] - 2026-07-05

### Added
- **A new Ability Cooldowns HUD widget tracks your mcMMO super abilities.** Super Breaker, Giga Drill Breaker, Tree Feller, Skull Splitter, Green Terra, Serrated Strikes, Berserk, Explosive Shot, Super Shotgun, and Blast Mining are now picked up straight from chat/actionbar and drawn as circular cooldown rings — an orange-to-green arc sweeps clockwise as each ability recharges, the ring glows solid teal while active, and pulses green the moment it's ready. Using a Smelling Salts instantly clears every tracked cooldown, instead of waiting on the "ABILITIES REFRESHED!" chat line.
- **The same widget now also tracks Pit item cooldowns**: Stormbreaker, Heavy Steel Chestplate, Excalibur, Blink Boots, Body Slam, and Scythe Sweep all show up alongside your mcMMO abilities.
- **Per-cooldown visibility toggles and a horizontal/vertical layout switch** for the Ability Cooldowns widget, both available in the HUD editor's inspector panel — hide the ones you don't care about, or stack them in a column instead of a row.
- **Cooldowns under a minute now show a precise decimal timer** (e.g. `10.5`) instead of a coarse `0:11`, and any HUD widget pinned near the bottom or right edge of the screen now grows away from that edge as its content changes size, instead of running off-screen.
- **The remaining-uses count on charge items (Whetstones, etc.) is now configurable.** A new Item Uses section in Mod Menu lets you turn it on or off, pick which corner of the item slot it's drawn in, and adjust its color and size (80%-110% of default).

### Changed
- **Mod Menu no longer lists a settings category for every HUD widget.** Those categories only duplicated what the in-game HUD editor (`;`) already lets you edit — position, size, colors, background — so widget appearance is now managed entirely in the editor, and Mod Menu is limited to the Bite Marker and the new Item Uses settings.

## [1.12.1] - 2026-07-04

### Added
- **The mod's HUD widgets now only appear on the MCLabs server.** Chemtainer, boosters, dailies, bounty, and the rest of the tracked panel widgets are populated purely from MCLabs' own chat, so they used to just sit there empty on any other server (or in singleplayer). They now switch on automatically the moment you join MCLabs and stay hidden everywhere else. The floating "!" bite marker is unaffected and keeps working on any server, since it doesn't depend on MCLabs at all.
- **The mod now lets you know in chat when a newer version is out.** Every time you join MCLabs, it checks Modrinth for a newer release and drops a quick local note if one exists — nothing is sent to the server, and it stays silent if you're offline or Modrinth is unreachable.

### Fixed
- **Withdrawing a chem from the Chemtainer could pull out 0 instead of the chem you have.** Chat would read `Withdrew 0 <Chem>-X-Y-Z from your chemtainer` even though that exact chem was sitting right there. The mod was reading a chem's purity numbers in the wrong order (e.g. reading a `2-0-2` chem as `2-2-0`), so the withdraw request asked the server for a purity variant that didn't exist. Purity is now read in the correct order, so the withdraw keybind pulls out the chem you actually have.

## [1.12.0] - 2026-06-28

### Added
- **A Chemtainer widget that shows what you've banked.** It lists your chems by quantity and estimates how many **inventories** they'd fill (e.g. `3.4 Inventories`), with a **Using Satchel** toggle in the HUD editor that switches the per-inventory capacity. It learns your contents three ways: opening `/ch` reads the exact contents, and two new keybinds keep it current as you play.
- **Deposit and Withdraw Chemtainer keybinds.** **Deposit Chemtainer** (default `B`) sends `/ch qd` and adds whatever chems just left your inventory; **Withdraw Chemtainer** (default `N`) runs `/ch withdraw` for whichever chem you have the most of and subtracts what the server reports. They work whether you press the key or type `/ch qd` yourself.
- **A welcome guide for new players.** The first time you open the HUD editor, a short thank-you/guide appears (once), and a new **Help** button in the editor reopens it any time. It lists every sync command the mod uses.
- **Normal (base) crops now track too.** Plain vanilla crops like Wheatium, Cactium, and Canium — which carry no special data, only the server texture pack's rename — are now recognised and counted in the Chemtainer just like combo chems.

### Changed
- **The mod's keybinds now have their own "McLab Addons" category** near the top of Options → Controls, instead of being mixed into vanilla Gameplay.
- **Better booster icons.** More chems map to their real item (e.g. **Canium** now shows sugar cane, not sugar), so booster rows are easier to tell apart.
- Polished the vote widget's override label to **"Mark Voted Today"**.

### Fixed
- **The Chemtainer widget no longer falls behind when you deposit fast.** Depositing quickly with `B` / `/ch qd`, or picking up more chems before the widget caught up, used to undercount — each deposit was guessed from a single before/after inventory snapshot, so anything you farmed in the meantime was subtracted straight out of the count, and a second deposit could wipe out the first. It now follows each chem leaving your inventory as it happens and checks the running tally against the server's own `Deposited N chems` total, so back-to-back deposits and farming-while-depositing both add up correctly. (Opening `/ch` still gives the exact contents and now takes priority over any in-progress estimate.)
- **Redeeming a Personal Prestige Progress Boost now starts its timer.** The chem-price boost was detected on redeem but the prestige boost wasn't (its chat line has no colon, unlike `/checkboost`), so the widget never showed it — now both are picked up.
- **The Chemtainer HUD no longer crashes on fast withdrawals on ARM Macs.** The render thread was iterating the live entry list while the chat thread could remove from it simultaneously; on Apple Silicon the two stores in `ArrayList.remove` (size decrement and null-slot write) can become visible out of order, producing a null element that caused a `NullPointerException` in the sort comparator and killed HUD rendering. The list is now copied inside the lock before it leaves `ChemtainerTracker`, so the render thread always works on a stable snapshot.

## [1.11.1] - 2026-06-20

### Added
- **Active boosters are now picked up from the `/chems` → "Booster(s) active!" GUI.** Opening that menu tracks any chem price booster already running — useful when you join mid-booster and missed the activation chat — and refreshes its countdown each time you open it, the same way `/lw rates` updates Lab Wars boosters.

### Fixed
- **The "All" chem booster now actually shows its end-crystal icon and "All" label.** The 1.11.0 change matched the literal name `all`, but the server announces this booster as "All Chems", so it fell back to a plain paper icon with no label. The widget now recognises every "All" variant (`All Chems`, `all_chem_booster`).

## [1.11.0] - 2026-06-20

### Added
- **Vote Reminder: manual "Mark voted today" override.** A new action in the widget's HUD-editor inspector marks all 7 daily votes done, hiding the reminder for the rest of the day; it returns automatically after the 9 PM Pacific reset. Handy when votes were cast on another device or the "Vote registered!" lines were missed. Local-only — it does not sync across machines.

### Fixed
- **The "All" booster now shows its name.** The all-booster HUD row already used the end-crystal icon but displayed only the multiplier and time, so it was indistinguishable from a chem booster; it now reads e.g. `All 2x 30:00`.
- **The Daily Investor Rewards reminder (`/sm claim`) now clears the moment you run the command.** Previously it only cleared once a confirmation chat line matched, so it lingered until the command was re-run. It is now dismissed as soon as you send `/sm claim` (matched exactly, so other subcommands can't trigger it); the confirmation-line detection remains as a fallback for claims made from another client.

### Changed
- The **Open HUD Editor** keybind now defaults to **semicolon** (`;`) instead of being unbound.

## [1.10.2] - 2026-06-16

### Fixed
- HUD widgets that sat under the editor's "Widgets" rail (e.g. the Votes reminder) could not be dragged out. A selected widget can now be grabbed and dragged even when it sits beneath the rail or inspector panel.

### Changed
- Default HUD widget positions are now laid out as a clean, evenly spaced right-anchored column. Previously every widget defaulted to the far-left edge, where it was hidden under the editor's "Widgets" rail and several widgets overlapped each other. Existing saved layouts are untouched; use the editor's "Reset all" to adopt the new defaults.

## [1.10.1] - 2026-06-16

### Fixed
- **HUD widgets and the bite marker no longer render in normal gameplay after a Feather client update.** A Feather update broke Fabric's `HudElementRegistry` HUD-layer dispatch, so every registered HUD element (all widgets *and* the floating bite marker) silently stopped drawing during normal play. The widgets still appeared in the HUD editor because the editor is a `Screen` drawn outside the HUD-layer system.

### Changed
- HUD elements are now drawn from the mod's own `InGameHud.render` tail mixin (`InGameHudMixin` → `HudRenderDispatcher`) instead of Fabric's `HudElementRegistry.addLast`. The injection is anchored to the render method's return rather than to individual vanilla HUD elements, so it survives client overlays such as Feather while remaining correct in vanilla. The bite marker's frame-matrix capture (`WorldRenderEvents.END_EXTRACTION`) is unchanged.

## [1.10.0] - 2026-06-16

### Added
- Bounty tracker HUD widget.
- Daily reminder and vote reminder HUD widgets.
- `/chum` sync for the Chum Bucket timer.
- Chem icons.

## [1.9.0] - 2026-06-15

### Added
- Initial release: fish-bite indicator, Chum Bucket and booster timers, mini-event and Pit trackers, Lab Wars revenue boosters, rental mount and personal booster timers, and the draggable "HUD Studio" widget editor.

[1.14.1]: https://github.com/Jade-Cod/mclabs-addons/releases/tag/v1.14.1
[1.14.0]: https://github.com/Jade-Cod/mclabs-addons/releases/tag/v1.14.0
[1.13.0]: https://github.com/Jade-Cod/mclabs-addons/releases/tag/v1.13.0
[1.12.1]: https://github.com/Jade-Cod/mclabs-addons/releases/tag/v1.12.1
[1.12.0]: https://github.com/Jade-Cod/mclabs-addons/releases/tag/v1.12.0
[1.11.1]: https://github.com/Jade-Cod/mclabs-addons/releases/tag/v1.11.1
[1.11.0]: https://github.com/Jade-Cod/mclabs-addons/releases/tag/v1.11.0
[1.10.2]: https://github.com/Jade-Cod/mclabs-addons/releases/tag/v1.10.2
[1.10.1]: https://github.com/Jade-Cod/mclabs-addons/releases/tag/v1.10.1
[1.10.0]: https://github.com/Jade-Cod/mclabs-addons/releases/tag/v1.10.0
[1.9.0]: https://github.com/Jade-Cod/mclabs-addons/releases/tag/v1.9.0
