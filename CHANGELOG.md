# Changelog

All notable changes to MCLabs Addons are documented here.
This project follows [Semantic Versioning](https://semver.org/).

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

[1.10.1]: https://github.com/Jade-Cod/mclabs-addons/releases/tag/v1.10.1
[1.10.0]: https://github.com/Jade-Cod/mclabs-addons/releases/tag/v1.10.0
[1.9.0]: https://github.com/Jade-Cod/mclabs-addons/releases/tag/v1.9.0
