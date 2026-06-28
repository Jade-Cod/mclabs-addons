# MCLabs Addons

A **client-side** Fabric mod for **Minecraft 1.21.11**, built for the MCLabs
server. It started as a fishing bite indicator and grew into a suite of
on-screen HUD timers that track server boosters and events by reading chat and
GUIs **passively**. Tracking never automates anything on its own; the only
commands it ever sends are the optional **Chemtainer deposit/withdraw keybinds**
you press yourself.

> Internal mod id is still `fishbite` (config lives at `config/fishbite.json`);
> the display name is "MCLabs Addons".

## Features

**Bite marker** — floats an exclamation mark over your own fishing bobber:
**yellow** while waiting, **red** the instant a fish is ready to reel in. Stays
pinned above the water when the bobber is pulled under. Drawn as a HUD overlay so
it survives optimization mods (EntityCulling / ImmediatelyFast / Sodium).

**Sound** — **Mute other players bobber sounds** replace your
bobber's catch splash with any registered Minecraft sound (click-to-open dropdown).

**HUD timer widgets** (all draggable, resizable, colorable — see *HUD editor*):

| Widget | Tracks | Source |
|--------|--------|--------|
| Chum Timer | Double-fish time | Right-click a Chum Bucket; "purchased N minutes of double fish for the whole lab" chat; **`/chum` syncs to the server's exact remaining time**; Fishing mini-events grant +30 min |
| Booster Timer | Server revenue boosters, each with its **chem icon** | "Booster activated!" chat + the `/chems` "Booster(s) active!" GUI (read only while you have it open, so you can pick up boosters you joined mid-way) |
| Mini-Event | Upcoming + active mini-events (type, time) | "Mini-Event …" chat |
| The Pit | Pit open window (stacks sponsor/extend) | "The Pit …" chat |
| Lab Wars Boosters | Per-category revenue boosts (multiplier + time) | "Lab Wars …" chat + the `/lw rates` GUI (read only while you have it open) |
| Rental Mount | Rental mount access time | "purchased temporary access" chat; or right-click a Mount Rental Coupon |
| Personal Boosters | Chem-price + prestige boosts | redeem chat + `/checkboost` output |
| **Bounty Chests** | Active Spawn Bounty Hunt — the bounty chemical + chests remaining (chest icon) | "Bounty »" start / found / ended chat |
| **Dailies** | Reminders to claim the daily spin (`/daily`) and Daily Investor Rewards (`/sm claim`) | claim-confirmation chat; resets 9 PM Pacific |
| **Vote Reminder** | Daily vote progress toward 7/7 | "Vote registered!" chat; resets 9 PM Pacific |
| **Chemtainer** | What's in your Chemtainer (chems by quantity + an "inventories" estimate) | the `/ch` GUI (read while open) **and** the Deposit/Withdraw keybinds, which diff your inventory and parse the "Withdrew N …" chat |

Timers persist across relogs (absolute expiry in config) and display as `M:SS`,
`H:MM:SS`, or `Xd Yh` for long durations. The **Dailies** and **Vote Reminder**
widgets reset at **9 PM Pacific**, computed as a real instant so they reset at the
correct local time wherever you play. The **Booster** and **Bounty** widgets show
each chemical's real in-game icon, skinned by the server resource pack (the "All"
booster shows an end crystal labelled "All").

## User Guide

### Install

1. Install **Fabric Loader** for Minecraft **1.21.11**, then drop **Fabric API**,
   **Cloth Config**, and (optionally) **Mod Menu** into your `mods` folder — see
   *Requirements* below.
2. Put `mclabs-addons-1.11.1.jar` in `mods` and launch. The mod is **client-side**,
   so it works on the MCLabs server with nothing installed server-side.

### First launch

- Start fishing — an exclamation mark appears over your bobber: **yellow** while
  waiting, **red** the instant a fish is ready to reel in.
- The mod's keys live under their own **McLab Addons** category in **Options →
  Controls** (near the top): **Open HUD Editor** (defaults to **semicolon** `;`),
  **Deposit Chemtainer** (default **B**), and **Withdraw Chemtainer** (default
  **N**). Rebind any of them there.
- The first time you open the HUD editor you'll get a short **welcome guide**; you
  can reopen it any time with the **Help** button in the editor toolbar.
- Adjust colors, sizes, and toggles in **Mods → MCLabs Addons → Config**.

### Using each tracker

Everything updates **passively** from chat — you never have to run anything special:

- **Chum / double fish** — right-click a Chum Bucket to start or extend the timer
  (holding right-click while scrolling across two buckets now counts both). Run
  **`/chum`** anytime to snap the timer to the server's exact remaining time; if the
  buff has expired, the widget clears itself.
- **Boosters** — appears automatically when someone activates a server booster,
  showing the chemical's icon, multiplier, and countdown. The "All Chems" booster
  shows an end crystal labelled "All". Joined after a booster started? Open
  **`/chems` → "Booster(s) active!"** and the widget picks up whatever is already
  running and refreshes its countdown, the same way `/lw rates` updates Lab Wars.
- **Bounty Hunt** — when a Bounty Hunt starts in Spawn, the chest widget shows the
  bounty chemical and how many chests remain, updating as players find them and
  hiding when the hunt ends. Use the server's **`/bounty track`** in Spawn to be led
  to a chest.
- **Dailies** — reminds you to claim your **daily spin (`/daily`)** and **Daily
  Investor Rewards (`/sm claim`)**. Each line disappears once you claim it — the
  `/sm claim` reminder clears the moment you send the command — and returns after
  the **9 PM Pacific** reset.
- **Vote Reminder** — counts your votes toward **7/7** as each "Vote registered!"
  arrives, and hides once all seven are done for the day (resets 9 PM Pacific). If
  you voted elsewhere or missed the chat lines, select the widget in the HUD editor
  and use **"Mark Voted Today"** to hide it until the next reset.
- **Chemtainer** — tracks what's banked in your Chemtainer. Opening **`/ch`** syncs
  the exact contents; or use the keybinds: **Deposit Chemtainer** (default **B**)
  sends `/ch qd` and adds whatever left your inventory, and **Withdraw Chemtainer**
  (default **N**) runs `/ch withdraw` for whichever chem you have the most of. The
  widget shows each chem by quantity plus an estimate of how many **inventories**
  it fills — toggle **Using Satchel** in the editor to switch the per-inventory
  capacity. Works with both base crops and combo chems.
- **Mini-Event, The Pit, Lab Wars, Rental Mount, Personal Boosters** — appear and
  count down whenever the matching server message or item shows up.

Don't see a widget? Each one only renders when it has something to show. Open the
HUD editor to preview and position every widget, including idle ones.

## HUD editor ("HUD Studio")

Press **"Open HUD Editor"** (in *Controls* → category: **McLab Addons**; defaults
to **semicolon** `;`) to enter the editor:

- **Layers rail** (left) — lists all widgets; click to select, eye icon to
  toggle visibility even on hidden widgets.
- **Drag** the widget body to move it; **8 edge/corner handles** to resize
  (corners scale uniformly; edges scale one axis). Snaps to screen
  edges, center lines, and other widgets.
- **Arrow keys** nudge the selected widget one pixel at a time.
- **Inspector** (docks on the opposite side from the selected widget) —
  toggle visibility, pick text/background color, reset the widget, and any
  widget-specific options (e.g. the Chemtainer's **Using Satchel** toggle).
- **Bottom toolbar** — Grid and Snap toggles, plus **Help** to reopen the guide.
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
  → per-feature trackers), the outgoing-command hook (`ClientSendMessageEvents`,
  used to clear the `/sm claim` reminder and to arm the Chemtainer deposit diff),
  item-use detection (`UseItemCallback`), the keybinds (HUD editor + Chemtainer
  deposit/withdraw, under a custom **McLab Addons** controls category), and the
  once-per-open `/lw rates`, `/chems`, and `/ch` GUI scrapes.
- `hud/` — the reusable widget framework: `HudObject` base (background, scale,
  auto side-anchoring, screen bounds), `HudObjectSettings`, `HudEditScreen`,
  `ColorPickerScreen`, `HelpScreen` (the welcome/Help guide), `TimeFormat`,
  `Durations`; `hud/editor/` holds `EditorTheme` constants and `EditorPainter`
  draw helpers used by the HUD Studio.
- One package per feature with a `*Tracker`/timer (chat parsing + persisted
  state) and a `*HudObject` widget: `chum/`, `booster/`, `event/` (mini-event +
  pit), `labwars/`, `mount/`, `personal/`, `bounty/`, and `daily/` (dailies +
  vote reminders). `chem/` holds the Chemtainer feature (`ChemtainerTracker`,
  `ChemtainerReader`, `ChemtainerDepositCapture`, `ChemtainerHudObject`), the
  `ChemItems`/`ChemBaseItems` chem-identity helpers, and `ChemIcons`, the
  chemical → item-icon map shared by the booster, bounty, and Chemtainer widgets;
  `daily/DailyReset` computes the 9 PM Pacific reset boundary.
- `mixin/` — `FishingBobberEntityAccessor` (synced `CAUGHT_FISH`),
  `SoundSystemMixin` (mute/replace bobber sounds), `CameraAccessor`,
  `InGameHudMixin` (bite-marker projection), and `KeyBindingCategoryAccessor`
  (hoists the McLab Addons keybind category near the top of Controls).

Design rule: **passive by default**. Chat is parsed; container GUIs are read only
while the player has them open. Nothing is auto-clicked or auto-closed. The sole
exception is the Chemtainer deposit/withdraw keybinds, which send a `/ch` command
only when the player presses the key.

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

Current mod version: **1.12.0-dev**.

## Building

Requires a **JDK 21**.

```bash
./gradlew build      # builds build/libs/fishbite-indicator-<version>.jar
./gradlew runClient  # launches a dev client
```

Toolchain: Fabric Loom 1.17.8, Gradle 9.5.1 (wrapper committed), Yarn mappings
`1.21.11+build.6`.
<!-- END AUTO-GENERATED -->
