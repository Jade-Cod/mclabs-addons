# Changelog

All notable changes to MCLabs Addons are documented here.
This project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

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

[1.11.1]: https://github.com/Jade-Cod/mclabs-addons/releases/tag/v1.11.1
[1.11.0]: https://github.com/Jade-Cod/mclabs-addons/releases/tag/v1.11.0
[1.10.2]: https://github.com/Jade-Cod/mclabs-addons/releases/tag/v1.10.2
[1.10.1]: https://github.com/Jade-Cod/mclabs-addons/releases/tag/v1.10.1
[1.10.0]: https://github.com/Jade-Cod/mclabs-addons/releases/tag/v1.10.0
[1.9.0]: https://github.com/Jade-Cod/mclabs-addons/releases/tag/v1.9.0
