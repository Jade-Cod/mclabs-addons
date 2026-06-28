package dev.jade.fishbite.config;

import com.google.gson.Gson;
import dev.jade.fishbite.booster.BoosterState;
import dev.jade.fishbite.chem.ChemtainerEntry;
import dev.jade.fishbite.labwars.LabWarsBooster;
import dev.jade.fishbite.hud.HudObjectSettings;
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
 * Mod configuration persisted to {@code config/fishbite.json}. Loaded lazily and
 * shared as a singleton across the renderer, sound hook, chum timer, and config
 * screen.
 */
public class FishBiteConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("fishbite");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH =
			FabricLoader.getInstance().getConfigDir().resolve("fishbite.json");
	private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor();
	private static final Object SAVE_LOCK = new Object();

	public static final int DEFAULT_WAITING_COLOR = 0xFFFF55;
	public static final int DEFAULT_BITE_COLOR = 0xFF5555;
	private static final float MIN_SCALE = 0.25f;
	private static final float MAX_SCALE = 4.0f;
	private static final int RGB_MASK = 0xFFFFFF;

	private static FishBiteConfig instance;

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

	// --- Chemtainer contents (authoritative snapshot scraped from the /ch GUI) ---
	public java.util.List<ChemtainerEntry> chemtainer = new java.util.ArrayList<>();
	/** When the contents above were last scraped (epoch ms); 0 = never opened. */
	public long chemtainerSnapshotMs = 0L;
	/** True if a deposit was seen in chat since the last scrape (contents stale). */
	public boolean chemtainerStale = false;
	/** Whether the player uses a satchel (changes the inventory-estimate divisor). */
	public boolean chemtainerSatchel = true;

	// --- HUD objects (position/scale/colors per widget id) ---
	public java.util.Map<String, HudObjectSettings> hudObjects = new java.util.LinkedHashMap<>();

	// Legacy v1.2.x fields, migrated into hudObjects on load.
	@Deprecated public Boolean chumTimerEnabled;
	@Deprecated public Float chumHudX;
	@Deprecated public Float chumHudY;

	public static FishBiteConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static FishBiteConfig load() {
		if (Files.exists(CONFIG_PATH)) {
			try (BufferedReader reader = Files.newBufferedReader(CONFIG_PATH)) {
				FishBiteConfig loaded = GSON.fromJson(reader, FishBiteConfig.class);
				if (loaded != null) {
					return loaded.sanitized();
				}
				LOGGER.warn("[fishbite] Config at {} was empty; restoring defaults.", CONFIG_PATH);
			} catch (IOException | JsonParseException e) {
				LOGGER.warn("[fishbite] Failed to read config at {}; using defaults.", CONFIG_PATH, e);
			}
		}

		FishBiteConfig defaults = new FishBiteConfig();
		defaults.save();
		return defaults;
	}

	/** Clamps loaded values into valid ranges without mutating this instance. */
	private FishBiteConfig sanitized() {
		FishBiteConfig clean = new FishBiteConfig();
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
		if (this.chemtainer != null) {
			for (ChemtainerEntry entry : this.chemtainer) {
				if (entry != null && entry.chem != null && entry.count > 0) {
					if (entry.purity == null) {
						entry.purity = "";
					}
					clean.chemtainer.add(entry);
				}
			}
		}
		clean.chemtainerSnapshotMs = Math.max(0L, this.chemtainerSnapshotMs);
		clean.chemtainerStale = this.chemtainerStale;
		clean.chemtainerSatchel = this.chemtainerSatchel;
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

	public void save() {
		synchronized (SAVE_LOCK) {
			try {
				Files.createDirectories(CONFIG_PATH.getParent());
				try (BufferedWriter writer = Files.newBufferedWriter(CONFIG_PATH)) {
					GSON.toJson(this, writer);
				}
			} catch (IOException e) {
				LOGGER.warn("[fishbite] Failed to write config at {}.", CONFIG_PATH, e);
			}
		}
	}

	public void saveAsync() {
		FishBiteConfig snapshot = this.sanitized();
		SAVE_EXECUTOR.submit(snapshot::save);
	}
}
