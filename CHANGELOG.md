# Changelog

## [1.14.0] - 2026-07-08

### Added
- **New Runner Jobs HUD widget.** Tracks MCLabs Runner job postings straight from chat — posted, completed, and failed counts plus the total money earned this session — drawn as a new draggable HUD widget, with a Reset Session action in the HUD editor's inspector panel.
- **A Runner Leaderboard ranks the runners working your jobs.** The HUD Studio has a new **Stats** button that opens a full-screen table of everyone who's completed a job you posted — each row shows their **player head**, jobs done and failed, **Completion %**, **Avg Time**, and total value sold, ranked by jobs completed. **Hover** a runner to watch their character model run in place, turned toward the list, beside it, and **click** a runner to roll open a shutter right under their row with their most recent completed jobs — drug, value, time taken and date, ten to a page with `<` / `>` turners — pushing the rows below it down; click again (or another runner) to roll it back up. It fills in automatically from chat as jobs finish and persists across relogs (average time is a rough estimate); open `/supplier` any time to re-sync how many jobs you have posted. Clearing the board now asks for confirmation first, so an accidental click can't wipe it.

### Changed
- **The mod's internal id is now `labsaddons`, completing the rename to MCLabs Addons.** Nothing changes in how the mod looks or works — everything carries over automatically on first launch:
  - All settings — HUD layouts, colors, timers, and tracked state — move from `config/fishbite.json` to `config/labsaddons.json`. The old file is left in place as a backup.
  - Custom key bindings for **Open HUD Editor**, **Deposit Chemtainer**, and **Withdraw Chemtainer** are carried over from Minecraft's `options.txt`, so rebound keys stay exactly where you put them.
- **Remove the old jar when updating.** If a 1.13.x-or-older jar is still in your `mods` folder, the game now stops at launch with a clear incompatibility message instead of silently running two copies of the mod — delete the old jar and relaunch.
- The build now produces `mclabs-addons-<version>.jar` directly (previously it built as `fishbite-indicator-<version>.jar` and was renamed at release time).

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
