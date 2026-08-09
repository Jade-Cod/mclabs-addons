package dev.jade.labsaddons.config;

import com.google.gson.Gson;
import dev.jade.labsaddons.booster.BoosterState;
import dev.jade.labsaddons.chem.ChemtainerEntry;
import dev.jade.labsaddons.labwars.LabWarsBooster;
import dev.jade.labsaddons.mastery.MasteryQuestEntry;
import dev.jade.labsaddons.hud.HudObjectSettings;
import dev.jade.labsaddons.item.ItemUsesCorner;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mod configuration persisted to {@code config/labsaddons.json}. Loaded lazily and
 * shared as a singleton across the renderer, sound hook, chum timer, and config
 * screen.
 */
public class LabsAddonsConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("labsaddons");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH =
			FabricLoader.getInstance().getConfigDir().resolve("labsaddons.json");
	/** Pre-rename (v1.13.x and older) location; read once, left in place as a backup. */
	private static final Path LEGACY_CONFIG_PATH =
			FabricLoader.getInstance().getConfigDir().resolve("fishbite.json");
	private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor();
	private static final Object SAVE_LOCK = new Object();

	public static final int DEFAULT_WAITING_COLOR = 0xFFFF55;
	public static final int DEFAULT_BITE_COLOR = 0xFF5555;
	public static final int DEFAULT_ITEM_USES_COLOR = 0xFF55FF55;
	public static final float DEFAULT_ITEM_USES_SCALE = 0.5f;
	private static final float MIN_SCALE = 0.25f;
	private static final float MAX_SCALE = 4.0f;
	private static final int RGB_MASK = 0xFFFFFF;
	private static final float MIN_ITEM_USES_SCALE = 0.4f;
	private static final float MAX_ITEM_USES_SCALE = 0.55f;

	private static LabsAddonsConfig instance;

	// --- Bite marker ---
	public boolean enabled = true;
	public float markerScale = 1.0f;
	public int waitingColor = DEFAULT_WAITING_COLOR;
	public int biteColor = DEFAULT_BITE_COLOR;
	public boolean muteOtherBobbers = false;
	public String catchSound = "";

	// --- Chum Bucket timer state ---
	public long chumExpiryEpochMs = 0L;

	// --- Mini-events & Pit (tracked from chat announcements) ---
	public String miniEventType = "";
	public long miniEventExpiryEpochMs = 0L;
	public long miniEventUpcomingEpochMs = 0L;
	public long pitExpiryEpochMs = 0L;

	// --- Rental mount & personal boosts (tracked from chat + items) ---
	public long rentalMountExpiryEpochMs = 0L;
	public long personalChemPriceExpiryMs = 0L;
	public long personalPrestigeExpiryMs = 0L;

	// --- Dailies & votes (reset at 9 PM Pacific; see daily.DailyReset) ---
	public long dailySpinClaimedMs = 0L;
	public long smClaimedMs = 0L;
	public int voteCount = 0;
	public long voteBoundaryMs = 0L;

	// --- Server boosters (tracked from chat announcements) ---
	public java.util.Map<String, BoosterState> boosters = new java.util.LinkedHashMap<>();

	// --- Lab Wars revenue boosters (stack per category; tracked from chat + /lw rates GUI) ---
	public java.util.List<LabWarsBooster> labWarsActive = new java.util.ArrayList<>();
	/** Legacy v1.6.x single-per-category map, migrated into labWarsActive on load. */
	@Deprecated public java.util.Map<String, LabWarsBooster> labWarsBoosters;

	// --- Onboarding ---
	/** True once the first-run welcome guide has been shown in the HUD editor. */
	public boolean hasSeenWelcome = false;
	/** True once the one-time pre-rename keybind carry-over has run (v1.14.0). */
	public boolean keybindsMigrated = false;

	// --- Chemtainer contents (authoritative snapshot scraped from the /ch GUI) ---
	public java.util.List<ChemtainerEntry> chemtainer = new java.util.ArrayList<>();
	/** When the contents above were last scraped (epoch ms); 0 = never opened. */
	public long chemtainerSnapshotMs = 0L;
	/** True if a deposit was seen in chat since the last scrape (contents stale). */
	public boolean chemtainerStale = false;
	/** Whether the player uses a satchel (changes the inventory-estimate divisor). */
	public boolean chemtainerSatchel = true;

	// --- Mastery challenges (progress widget) ---
	/** Last known board, restored on launch so chat reactions count before the first /mastery. */
	public java.util.List<MasteryQuestEntry> masteryQuests = new java.util.ArrayList<>();
	/** When the board above was last scraped or advanced (epoch ms); 0 = never. */
	public long masterySnapshotMs = 0L;

	// --- Chem prestige (progress widget) ---
	/** Last known tracks, restored on launch so a sale counts before the first sync. */
	public java.util.List<dev.jade.labsaddons.prestige.PrestigeChemEntry> prestigeChems =
			new java.util.ArrayList<>();

	/**
	 * Row names (lowercased) kept on screen permanently — Mastery quest names and chem
	 * prestige names alike. Everything else only appears while it is gaining.
	 */
	public java.util.Set<String> pinnedProgressRows = new java.util.LinkedHashSet<>();

	// --- Ability cooldowns widget (mcMMO + future cooldown sources) ---
	/** Stack cooldown rings in a column instead of a row. */
	public boolean cooldownsStackVertical = false;
	/** Cooldown entry keys (e.g. "mcmmo:super_breaker") hidden from the widget. */
	public java.util.Set<String> hiddenCooldownKeys = new java.util.LinkedHashSet<>();

	// --- HUD objects (position/scale/colors per widget id) ---
	public java.util.Map<String, HudObjectSettings> hudObjects = new java.util.LinkedHashMap<>();

	// --- Runner leaderboard (per-runner all-time stats; supplier side) ---
	public java.util.Map<String, RunnerStats> runnerStats = new java.util.LinkedHashMap<>();

	// --- Runner job alarm (low-jobs alert) ---
	public boolean runnerAlarmEnabled = false;
	public int runnerAlarmThreshold = 1;
	public String runnerAlarmSound = dev.jade.labsaddons.runner.RunnerAlarm.DEFAULT_SOUND;

	// --- Item Uses overlay (remaining charges shown on inventory slots) ---
	public boolean itemUsesEnabled = true;
	public String itemUsesCorner = ItemUsesCorner.TOP_LEFT.name();
	public int itemUsesColor = DEFAULT_ITEM_USES_COLOR;
	public float itemUsesScale = DEFAULT_ITEM_USES_SCALE;

	// Legacy v1.2.x fields, migrated into hudObjects on load.
	@Deprecated public Boolean chumTimerEnabled;
	@Deprecated public Float chumHudX;
	@Deprecated public Float chumHudY;

	public static LabsAddonsConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static LabsAddonsConfig load() {
		ConfigMigration.migrate(CONFIG_PATH, LEGACY_CONFIG_PATH);
		if (Files.exists(CONFIG_PATH)) {
			try (BufferedReader reader = Files.newBufferedReader(CONFIG_PATH)) {
				LabsAddonsConfig loaded = GSON.fromJson(reader, LabsAddonsConfig.class);
				if (loaded != null) {
					return loaded.sanitized();
				}
				LOGGER.warn("[labsaddons] Config at {} was empty; restoring defaults.", CONFIG_PATH);
			} catch (IOException | JsonParseException e) {
				LOGGER.warn("[labsaddons] Failed to read config at {}; using defaults.", CONFIG_PATH, e);
			}
		}

		LabsAddonsConfig defaults = new LabsAddonsConfig();
		defaults.save();
		return defaults;
	}

	/** Clamps loaded values into valid ranges without mutating this instance. */
	private LabsAddonsConfig sanitized() {
		LabsAddonsConfig clean = new LabsAddonsConfig();
		clean.enabled = this.enabled;
		clean.markerScale = Math.clamp(this.markerScale, MIN_SCALE, MAX_SCALE);
		clean.waitingColor = this.waitingColor & RGB_MASK;
		clean.biteColor = this.biteColor & RGB_MASK;
		clean.muteOtherBobbers = this.muteOtherBobbers;
		clean.catchSound = this.catchSound == null ? "" : this.catchSound.trim();
		clean.chumExpiryEpochMs = Math.max(0L, this.chumExpiryEpochMs);
		clean.miniEventType = this.miniEventType == null ? "" : this.miniEventType;
		clean.miniEventExpiryEpochMs = Math.max(0L, this.miniEventExpiryEpochMs);
		clean.miniEventUpcomingEpochMs = Math.max(0L, this.miniEventUpcomingEpochMs);
		clean.pitExpiryEpochMs = Math.max(0L, this.pitExpiryEpochMs);
		clean.rentalMountExpiryEpochMs = Math.max(0L, this.rentalMountExpiryEpochMs);
		clean.personalChemPriceExpiryMs = Math.max(0L, this.personalChemPriceExpiryMs);
		clean.personalPrestigeExpiryMs = Math.max(0L, this.personalPrestigeExpiryMs);
		clean.dailySpinClaimedMs = Math.max(0L, this.dailySpinClaimedMs);
		clean.smClaimedMs = Math.max(0L, this.smClaimedMs);
		clean.voteCount = Math.max(0, this.voteCount);
		clean.voteBoundaryMs = Math.max(0L, this.voteBoundaryMs);
		clean.hasSeenWelcome = this.hasSeenWelcome;
		clean.keybindsMigrated = this.keybindsMigrated;
		if (this.chemtainer != null) {
			for (ChemtainerEntry entry : this.chemtainer) {
				if (entry != null && entry.chem != null && entry.count > 0) {
					clean.chemtainer.add(new ChemtainerEntry(
							entry.chem,
							entry.purity == null ? "" : entry.purity,
							entry.label == null ? "" : entry.label,
							entry.count));
				}
			}
		}
		clean.chemtainerSnapshotMs = Math.max(0L, this.chemtainerSnapshotMs);
		clean.chemtainerStale = this.chemtainerStale;
		clean.chemtainerSatchel = this.chemtainerSatchel;
		if (this.masteryQuests != null) {
			for (MasteryQuestEntry entry : this.masteryQuests) {
				// A quest with no name cannot be matched by the chat tracker, and a
				// non-positive target would divide by zero in the bar; drop both.
				if (entry != null && entry.name != null && !entry.name.isBlank() && entry.target > 0) {
					clean.masteryQuests.add(new MasteryQuestEntry(
							entry.icon == null ? "" : entry.icon,
							entry.name,
							Math.max(0, entry.current),
							entry.target,
							Math.clamp(entry.percent, 0, 100)));
				}
			}
		}
		clean.masterySnapshotMs = Math.max(0L, this.masterySnapshotMs);
		if (this.prestigeChems != null) {
			for (dev.jade.labsaddons.prestige.PrestigeChemEntry entry : this.prestigeChems) {
				// A nameless chem can never be matched by a sale; a non-positive target
				// would divide by zero in the bar, which only a finished track may have
				// (the server states no figures once a chem is done).
				if (entry != null && entry.chem != null && !entry.chem.isBlank()
						&& (entry.target > 0 || entry.unlocked)) {
					clean.prestigeChems.add(new dev.jade.labsaddons.prestige.PrestigeChemEntry(
							entry.chem, Math.max(0, entry.current), Math.max(0, entry.target),
							entry.unlocked));
				}
			}
		}
		if (this.pinnedProgressRows != null) {
			this.pinnedProgressRows.stream().filter(java.util.Objects::nonNull)
					.forEach(clean.pinnedProgressRows::add);
		}
		clean.cooldownsStackVertical = this.cooldownsStackVertical;
		clean.itemUsesEnabled = this.itemUsesEnabled;
		clean.itemUsesCorner = parseCorner(this.itemUsesCorner).name();
		clean.itemUsesColor = this.itemUsesColor;
		clean.itemUsesScale = Math.clamp(this.itemUsesScale, MIN_ITEM_USES_SCALE, MAX_ITEM_USES_SCALE);
		clean.runnerAlarmEnabled = this.runnerAlarmEnabled;
		clean.runnerAlarmThreshold = Math.max(0, this.runnerAlarmThreshold);
		clean.runnerAlarmSound = dev.jade.labsaddons.runner.RunnerAlarm.isValidSound(this.runnerAlarmSound)
				? this.runnerAlarmSound : dev.jade.labsaddons.runner.RunnerAlarm.DEFAULT_SOUND;
		if (this.hiddenCooldownKeys != null) {
			this.hiddenCooldownKeys.stream().filter(java.util.Objects::nonNull)
					.forEach(clean.hiddenCooldownKeys::add);
		}
		if (this.labWarsActive != null) {
			for (LabWarsBooster b : this.labWarsActive) {
				if (b != null && b.key != null) {
					clean.labWarsActive.add(b);
				}
			}
		}
		if (clean.labWarsActive.isEmpty() && this.labWarsBoosters != null) {
			this.labWarsBoosters.values().forEach(b -> {
				if (b != null && b.key != null) {
					clean.labWarsActive.add(b);
				}
			});
		}
		if (this.boosters != null) {
			this.boosters.forEach((key, booster) -> {
				if (key != null && booster != null && booster.item != null) {
					clean.boosters.put(key, booster);
				}
			});
		}
		if (this.hudObjects != null) {
			this.hudObjects.forEach((id, settings) -> {
				if (id != null && settings != null) {
					settings.sanitize();
					clean.hudObjects.put(id, settings);
				}
			});
		}
		if (this.runnerStats != null) {
			this.runnerStats.forEach((name, stats) -> {
				if (name != null && stats != null) {
					stats.sanitize();
					clean.runnerStats.put(name, stats);
				}
			});
			// Bound a (possibly griefed) persisted map; eldest entries dropped.
			while (clean.runnerStats.size() > RunnerStats.MAX_RUNNERS) {
				clean.runnerStats.remove(clean.runnerStats.keySet().iterator().next());
			}
		}
		// Migrate v1.2.x chum HUD fields into the generic map.
		if (!clean.hudObjects.containsKey("chum_timer")
				&& (this.chumTimerEnabled != null || this.chumHudX != null)) {
			HudObjectSettings chum = new HudObjectSettings();
			chum.enabled = this.chumTimerEnabled == null || this.chumTimerEnabled;
			chum.x = this.chumHudX == null ? 0.012f : Math.clamp(this.chumHudX, 0.0f, 1.0f);
			chum.y = this.chumHudY == null ? 0.40f : Math.clamp(this.chumHudY, 0.0f, 1.0f);
			chum.textColor = 0xFF55FFFF;
			clean.hudObjects.put("chum_timer", chum);
		}
		return clean;
	}

	private static ItemUsesCorner parseCorner(String value) {
		try {
			return ItemUsesCorner.valueOf(value);
		} catch (IllegalArgumentException | NullPointerException e) {
			return ItemUsesCorner.TOP_LEFT;
		}
	}

	public void save() {
		synchronized (SAVE_LOCK) {
			try {
				Files.createDirectories(CONFIG_PATH.getParent());
				try (BufferedWriter writer = Files.newBufferedWriter(CONFIG_PATH)) {
					GSON.toJson(this, writer);
				}
			} catch (IOException e) {
				LOGGER.warn("[labsaddons] Failed to write config at {}.", CONFIG_PATH, e);
			}
		}
	}

	public void saveAsync() {
		LabsAddonsConfig snapshot = this.sanitized();
		SAVE_EXECUTOR.submit(snapshot::save);
	}
}
