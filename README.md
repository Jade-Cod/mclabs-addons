# MCLabs Addons

A **client-side** Fabric mod for **Minecraft 1.21.11** and **26.2**, built for the MCLabs
server. It started as a fishing bite indicator and grew into a suite of
on-screen HUD timers that track server boosters and events by reading chat and
GUIs **passively**. Tracking never automates anything on its own. The only
commands it sends are the optional **Chemtainer deposit/withdraw keybinds** you
press yourself, and a single `/cf stats` the first time you ever open the
coinflip lobby, to seed your coinflip record.

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
| Raid Mine | Double mine drops countdown, plus session totals and per-hour rates for every resource gathered | "Double mine drops for N seconds!" chat; resource holograms read from the world (the mine drops no items) |
| Lab Wars Boosters | Per-category revenue boosts (multiplier + time) | "Lab Wars …" chat + the `/lw rates` GUI (read only while you have it open) |
| Rental Mount | Rental mount access time | "purchased temporary access" chat; or right-click a Mount Rental Coupon |
| Personal Boosters | Chem-price + prestige boosts | redeem chat + `/checkboost` output |
| **Bounties** | Active Spawn Bounty Hunt — the bounty chemical + chests remaining (chest icon) — and the Fishing Weekend's Sunken Treasure barrels remaining (barrel icon) | "Bounty »" start / found / ended chat; "Fishing Weekend »" sunken treasure chat + the `/fw` GUI (read while open) |
| **Dailies** | Reminders to claim the daily spin (`/daily`) and Daily Investor Rewards (`/sm claim`) | claim-confirmation chat; resets 9 PM Pacific |
| **Vote Reminder** | Daily vote progress toward 7/7 | "Vote registered!" chat; resets 9 PM Pacific |
| **Chemtainer** | What's in your Chemtainer (chems by quantity + an "inventories" estimate) | the `/ch` GUI (read while open) **and** the Deposit/Withdraw keybinds, which diff your inventory and parse the "Withdrew N …" chat |
| **Runner Jobs** | Your posted/completed/failed jobs and money earned this session, with an optional low-jobs alarm and a per-runner leaderboard | "Runner »" / "MCLabs »" chat; the **`/supplier` GUI re-syncs your open job count** |
| **Mastery & Prestige** | Progress bars for your 5 active Mastery challenges, your 14 chem prestige tracks and your police contraband tier, with the amount just earned | the `/mastery` GUI (read while open) + chat; **`/prestige progress`**, then each sale's prestige line (the amount printed in chat, or each chem's share in its hover) and each confiscation's `Earned N progress` line |
| **Rented Items** | Everything you have taken through `/rent`: icon, name and time left, with a return reminder (title + sound) at 1–60 minutes left | the rented item's own lore; the extend and return chat lines; **`/rent return` re-syncs every rental** |
| **Open Coinflips** | Every coinflip posted and not yet taken, with its id | the server's coinflip announcements |
| **Coinflip Record** | What you have won, lost and wagered at coinflip | `/cf stats` once, then each result line |

Timers persist across relogs (absolute expiry in config) and display as `M:SS`,
`H:MM:SS`, or `Xd Yh` for long durations. The **Dailies** and **Vote Reminder**
widgets reset at **9 PM Pacific**, computed as a real instant so they reset at the
correct local time wherever you play. The **Booster** and **Bounty** widgets show
each chemical's real in-game icon, skinned by the server resource pack (the "All"
booster shows an end crystal labelled "All").

**Casino boards and crates** — the casino games and crate openings are chest menus
full of stained glass. The mod draws its own panel in the chest's place:
Double²'s wheel (`/double`), a 5x5 Mines grid with its payout ladder (`/mines`),
a blackjack card table (`/bj`), a coinflip lobby and coin (`/cf`), a crate
chamber with your pity-ladder odds, and a voter crate's three-way choice with the
published odds of each draw. Every control forwards a click to the slot you would
have clicked yourself, only when you click it, so the server sees an ordinary
click. Each board has its own switch in Mod Menu; off gives back the server's
chest menu, untouched.

## User Guide

### Install

1. Install **Fabric Loader** for Minecraft **1.21.11** or **26.2**, then drop **Fabric API**
   into your `mods` folder. **Mod Menu** and **Cloth Config** are both optional —
   add them only if you want the Mod Menu settings screen. See *Requirements* below.
2. Download the build for your Minecraft version and put it in `mods`. Modrinth and
   CurseForge pick the right file for you; on GitHub the jars are named
   `mclabs-addons-1.17.0-mc1.21.11.jar` and `mclabs-addons-1.17.0-mc26.2.jar`. The
   mod is **client-side**, so it works on the MCLabs server with nothing installed
   server-side.

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
- **Sunken Treasure** — during a Fishing Weekend the same widget gains a barrel row
  counting the sunken barrels still hidden along Spawn's shorelines, updating as
  players find them and going away when the last one is claimed. Each new wave
  re-seeds the count on its own, and opening **`/fw`** re-syncs it if you joined
  partway through and missed the announcement. A wave nobody finishes disappears
  after an hour with no treasure news, and opening `/fw` during any other weekend
  event clears a leftover row.
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
- **Runner Jobs** — tracks the jobs you post as a supplier: posted, completed, and
  failed counts plus money earned this session, read straight from chat. Open
  **`/supplier`** any time to re-sync your open job count. Turn on the **low-jobs
  alarm** in the editor to get a red on-screen title and a double alert tone the
  moment your posted jobs drop to your chosen threshold. The **Stats** button in
  the HUD editor opens a per-runner leaderboard that persists across relogs.
- **Mastery & Prestige** — one widget for both progress systems, drawn as exp-style
  bars. Open **`/mastery`** to sync your 5 active challenges and run
  **`/prestige progress`** to sync your 14 chem prestige tracks; after that both
  advance live from chat as you sell, kill, catch, or win chat reactions, showing
  the amount just earned beside the bar. By default a row only appears while it is
  gaining and then fades, so the widget stays out of the way — **pin** any row in
  the HUD editor (under *Keep On Screen*) to keep it up permanently. Prestige
  figures come from the server rather than being calculated. Selling a single raw
  chem prints its amount in chat (`Earned 104 prestige progress for Cactium.`); a
  sale that earns progress for several chems, raw or compound, carries each chem's
  share in its hover instead. Finished chem tracks are marked complete and stop
  counting. Police prestige moves from each confiscation's own figure; run
  `/prestige progress` once to pick up your contraband tier.
- **Rented Items** — anything taken through `/rent` gets a row with its icon, name
  and time left, read off the item's own lore. Extending and returning update it
  from chat, and opening **`/rent return`** re-syncs every rental you hold. The
  **Return Reminder** (on by default, 10 minutes, set in the widget's settings)
  puts up a title and plays a sound once per rental. Dying with a rental or
  breaking it isn't read yet: open `/rent return` or clear the widget in the HUD
  editor.
- **Coinflip** — **Open Coinflips** lists every flip posted and not yet taken.
  **Coinflip Record** is seeded by one `/cf stats` the first time you open the
  lobby and then keeps itself current from each result.
- **Mini-Event, The Pit, Lab Wars, Rental Mount, Personal Boosters** — appear and
  count down whenever the matching server message or item shows up.
- **Raid Mine** — counts down the double mine drops buff (procs stack, so a fresh
  roll extends rather than restarts it) and totals up what the session has
  gathered, with a per-hour rate for each resource. Breaking a mine block drops no
  items, so the totals come from the holograms the server shows in their place.
  Rows are drawn with the server's own coloured symbol to stay compact; the full
  resource names appear in the HUD editor. Only visible during a raid; totals clear
  only via **Reset Session** in the HUD editor, where each resource also gets its
  own show/hide switch.

Don't see a widget? Each one only renders when it has something to show. Open the
HUD editor to preview and position every widget, including idle ones.

## HUD editor ("HUD Studio")

Press **"Open HUD Editor"** (in *Controls* → category: **McLab Addons**; defaults
to **semicolon** `;`) to enter the editor:

- **Layers rail** (left) — lists all widgets alphabetically, with related ones
  folded into groups (**Boosts**, **Events**, **Reminders**, **Gambling**); click
  to select, eye icon to toggle visibility even on hidden widgets, and a group's
  toggle switches everything in it. Click the **Widgets** header to roll
  the list up to its title bar and free the corner behind it; it stays that way
  until you roll it back down. While you drag, resize or arrow-nudge a widget both
  the rail and the settings panel fade back so you can see the widget travelling
  underneath them.
- **Drag** the widget body to move it; **8 edge/corner handles** to resize
  (corners scale uniformly; edges scale one axis). Snaps to screen
  edges, center lines, and other widgets.
- **Arrow keys** nudge the selected widget one pixel at a time.
- **Inspector** (docks on the opposite side from the selected widget) —
  toggle visibility, pick text/background color, reset the widget, and any
  widget-specific options (e.g. the Chemtainer's **Using Satchel** toggle).
- **Bottom toolbar** — Grid and Snap toggles, **Profile** to manage HUD profiles,
  plus **Help** to reopen the guide.
- Widgets on the right half of the screen **anchor their right edge** and grow
  leftward, so growing text never runs off-screen.

### HUD profiles

A profile is a complete widget layout — position, size, colours, and which
widgets are switched on — together with the display choices inside them (pinned
progress rows, hidden ability cooldowns, shown Raid Mine resources, cooldown
stacking). Everything else stays shared across profiles.

Open **Profile** in the editor toolbar to switch, create, rename or delete one,
and to bind a profile to a world. Type a name and press **Create** to copy the
active layout into a new profile; **Rename** renames whichever profile is
currently active (`default` is kept as the fallback and can't be renamed). Bound worlds
load their profile automatically when you arrive — the mod reads the server's
own join banner, so it costs nothing and needs no command.

The bindable worlds are **Spawn**, the **Overworld**, the **Underworld**,
**Events** and **The Pit**. A world you leave unbound keeps whichever profile is
already loaded.

## Configuration

Open **Mods → MCLabs Addons → Config** (requires both
[Mod Menu](https://modrinth.com/mod/modmenu) and
[Cloth Config](https://modrinth.com/mod/cloth-config); without either one the
settings button simply doesn't appear). The screen has two categories: **Bite
Marker** (enable/size/colors, mute-others, catch sound) and **Item Uses**.
Everything about widget *appearance* — position, size, colors, background —
lives in the in-game HUD editor instead.

Saved under `config/labsaddons/`, split by how the data behaves:

| File | Holds |
| --- | --- |
| `settings.json` | Your preferences, and which profile is active |
| `state.json` | Server state the mod tracks (timers, boosters, scraped boards) |
| `runners.json` | The runner leaderboard — the only file that grows |
| `profiles/*.json` | One HUD layout each |

Files are written atomically and only when their contents change. A config from
an older version (`config/labsaddons.json`, or `config/fishbite.json` from
before the rename) is migrated on first launch and left in place as a backup.

## Architecture

- `LabsAddonsClient` (entrypoint) registers the bite-marker render callbacks, all
  HUD widgets via `hud/HudObjects`, the chat dispatch (`ClientReceiveMessageEvents`
  → per-feature trackers), the outgoing-command hook (`ClientSendMessageEvents`,
  used to clear the `/sm claim` reminder and to arm the Chemtainer deposit diff),
  item-use detection (`UseItemCallback`), the keybinds (HUD editor + Chemtainer
  deposit/withdraw, under a custom **McLab Addons** controls category), and the
  once-per-open GUI scrapes (`/lw rates`, `/chems`, `/ch`, `/supplier`, `/mastery`,
  `/prestige`, `/fw`, `/rent return`, crate odds).
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
- `runner/` — Runner job tracking: chat parsing (`RunnerMessages`), the session
  counters and per-runner leaderboard (`RunnerLeaderboard`), the low-jobs alarm,
  and the `/supplier` GUI scrape. `mcmmo/` and `pititem/` feed the shared
  `cooldown/` ring widget from mcMMO super-ability and Pit-item chat.
  `item/` draws the remaining-uses count on charge items; `server/McLabsSession`
  detects whether the player is actually on MCLabs.
- `casino/` is the shared base for the boards drawn over casino menus
  (`CasinoPanel`: click forwarding, container hold). `double2/`, `mines/`,
  `blackjack/` and `coinflip/` are the games, and `crate/` holds the crate chamber,
  pity ladder and voter-crate odds. `rental/` tracks `/rent` items and their return
  reminder, and `police/` feeds contraband prestige and the Patrol challenges.
- `mastery/` and `prestige/`: the two sources behind the progress widget.
  `MasteryReader` scrapes `/mastery`, and the kill, catch, sell and chat trackers
  advance challenges live. `PrestigeChat` reads `/prestige progress` and both
  sale-line formats (hover figures via `TextHovers`). `MasteryStore` and
  `PrestigeStore` persist both boards.
- `config/`: `LabsAddonsConfig` (one object, each field tagged with the file it
  belongs to via `@Section`), `ConfigStore` (atomic, coalesced per-file writes
  under `config/labsaddons/`) and the migrations from older config files.
  `server/McLabsWorld` reads the join banner that loads a bound HUD profile, and
  `hud/HudProfileScreen` manages profiles.
- `update/` — a once-per-join Modrinth check (`ModrinthUpdateChecker`,
  `ModVersion`, `ModrinthLink`) that posts a local-only chat line when a newer
  release exists; clicking it opens that release's Modrinth page.
- `mixin/` — `FishingHookAccessor` (synced `DATA_BITING`),
  `SoundEngineMixin` (mute/replace bobber sounds),
  `HudMixin` (bite-marker projection + HUD render tail),
  `GuiGraphicsExtractorMixin` (the item-uses overlay),
  `KeyMappingCategoryAccessor` (hoists the McLab Addons keybind category near
  the top of Controls), `TextDisplayInvoker` (reads the Raid Mine's gain
  holograms), `AbstractContainerScreenMixin` / `ContainerScreenMixin` (draw the
  casino and crate boards over their menus and route clicks to them), and
  `ClientPacketListenerMixin` (server-closed menus, clicked chat commands, and
  damage attribution for Pit kills).

Design rule: **passive by default**. Chat is parsed; container GUIs are read only
while the player has them open. Nothing is auto-clicked or auto-closed: the casino
boards send a slot click only for a click the player made on them. The commands
the mod sends are the Chemtainer deposit/withdraw keybinds (a `/ch` command only
when the player presses the key) and one `/cf stats` the first time the coinflip
lobby is ever opened.

<!-- AUTO-GENERATED: from gradle.properties + build.gradle — do not hand-edit -->
## Requirements

Drop these in your `mods` folder alongside the mod:

| Dependency | Version | Notes |
|------------|---------|-------|
| Minecraft | 26.2 exactly | |
| Fabric Loader | ≥ 0.17.3 | |
| [Fabric API](https://modrinth.com/mod/fabric-api) | 0.159.0+26.2 | required |
| [Mod Menu](https://modrinth.com/mod/modmenu) | 20.0.1 | optional (config screen) |
| [Cloth Config](https://modrinth.com/mod/cloth-config) | 26.2.155 | optional (widgets on that screen) |

Current mod version: **1.17.0**.

## Building

Requires a **JDK 25**. Gradle fetches one automatically if you don't have it.

```bash
./gradlew build      # builds build/libs/mclabs-addons-<version>.jar
./gradlew runClient  # launches a dev client
```

Toolchain: Fabric Loom 1.17.20 (non-remapping), Gradle 9.5.1 (wrapper
committed), Mojang official mappings — 26.x ships deobfuscated, so there is no
Yarn or intermediary step.
<!-- END AUTO-GENERATED -->
