# MCLabs Addons

A **client-side** Fabric mod for **Minecraft 1.21.11**, built for the MCLabs
server. It started as a fishing bite indicator and grew into a suite of
on-screen HUD timers that track server boosters and events by reading chat and
GUIs **passively** — it never sends commands or automates anything.

> Internal mod id is still `fishbite` (config lives at `config/fishbite.json`);
> the display name is "MCLabs Addons".

## Features

**Bite marker** — floats an exclamation mark over your own fishing bobber:
**yellow** while waiting, **red** the instant a fish is ready to reel in. Stays
pinned above the water when the bobber is pulled under. Drawn as a HUD overlay so
it survives optimization mods (EntityCulling / ImmediatelyFast / Sodium).

**Sound** — optionally mute other players' bobber sounds, and replace your
bobber's catch splash with any registered Minecraft sound (click-to-open dropdown).

**HUD timer widgets** (all draggable, resizable, colorable — see *HUD editor*):

| Widget | Tracks | Source |
|--------|--------|--------|
| Chum Timer | Double-fish time | Right-click a Chum Bucket; or "purchased N minutes of double fish for the whole lab" chat; Fishing mini-events grant +30 min |
| Booster Timer | Server revenue boosters | "Booster activated!" chat |
| Mini-Event | Upcoming + active mini-events (type, time) | "Mini-Event …" chat |
| The Pit | Pit open window (stacks sponsor/extend) | "The Pit …" chat |
| Lab Wars Boosters | Per-category revenue boosts (multiplier + time) | "Lab Wars …" chat + the `/lw rates` GUI (read only while you have it open) |
| Rental Mount | Rental mount access time | "purchased temporary access" chat; or right-click a Mount Rental Coupon |
| Personal Boosters | Chem-price + prestige boosts | redeem chat + `/checkboost` output |

Timers persist across relogs (absolute expiry in config) and display as `M:SS`,
`H:MM:SS`, or `Xd Yh` for long durations.

## HUD editor ("HUD Studio")

Bind **"Open HUD Editor"** in *Controls* (category: Gameplay; unbound by
default), then press it to enter the editor:

- **Layers rail** (left) — lists all widgets; click to select, eye icon to
  toggle visibility even on hidden widgets.
- **Drag** the widget body to move it; **8 edge/corner handles** to resize
  (corners scale uniformly; edges scale one axis). Snaps to screen
  edges, center lines, and other widgets.
- **Arrow keys** nudge the selected widget one pixel at a time.
- **Inspector** (docks on the opposite side from the selected widget) —
  toggle visibility, pick text/background color, and reset the widget.
- **Bottom toolbar** — Grid and Snap toggles.
- Widgets on the right half of the screen **anchor their right edge** and grow
  leftward, so growing text never runs off-screen.

## Configuration

Open **Mods → MCLabs Addons → Config** (requires
[Mod Menu](https://modrinth.com/mod/modmenu)). The screen has a **General**
category (bite-marker enable/size/colors, mute-others, catch sound) plus one
category per HUD widget (enable, size, text color, background). Saved to
`config/fishbite.json`.

## Architecture

- `FishBiteClient` (entrypoint) registers the bite-marker render callbacks, all
  HUD widgets via `hud/HudObjects`, the chat dispatch (`ClientReceiveMessageEvents`
  → per-feature trackers), item-use detection (`UseItemCallback`), and the editor
  keybind + the once-per-open `/lw rates` GUI scrape.
- `hud/` — the reusable widget framework: `HudObject` base (background, scale,
  auto side-anchoring, screen bounds), `HudObjectSettings`, `HudEditScreen`,
  `ColorPickerScreen`, `TimeFormat`, `Durations`; `hud/editor/` holds
  `EditorTheme` constants and `EditorPainter` draw helpers used by the HUD
  Studio.
- One package per feature with a `*Tracker`/timer (chat parsing + persisted
  state) and a `*HudObject` widget: `chum/`, `booster/`, `event/` (mini-event +
  pit), `labwars/`, `mount/`, `personal/`.
- `mixin/` — `FishingBobberEntityAccessor` (synced `CAUGHT_FISH`),
  `SoundSystemMixin` (mute/replace bobber sounds).

Design rule: **passive only**. Chat is parsed; container GUIs are read only while
the player has them open. Nothing is auto-clicked, auto-closed, or auto-typed.

<!-- AUTO-GENERATED: from gradle.properties + build.gradle — do not hand-edit -->
## Requirements

Drop these in your `mods` folder alongside the mod:

| Dependency | Version | Notes |
|------------|---------|-------|
| Minecraft | 1.21.11 | |
| Fabric Loader | ≥ 0.19.2 | |
| [Fabric API](https://modrinth.com/mod/fabric-api) | 0.141.4+1.21.11 | required |
| [Cloth Config](https://modrinth.com/mod/cloth-config) | 21.11.153 | required (config widgets) |
| [Mod Menu](https://modrinth.com/mod/modmenu) | 17.0.0 | optional (config screen) |

Current mod version: **1.9.0**.

## Building

Requires a **JDK 21**.

```bash
./gradlew build      # builds build/libs/fishbite-indicator-<version>.jar
./gradlew runClient  # launches a dev client
```

Toolchain: Fabric Loom 1.17.8, Gradle 9.5.1 (wrapper committed), Yarn mappings
`1.21.11+build.6`.
<!-- END AUTO-GENERATED -->
