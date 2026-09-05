package dev.jade.labsaddons.config;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.jade.labsaddons.booster.BoosterState;
import dev.jade.labsaddons.chem.ChemtainerEntry;
import dev.jade.labsaddons.labwars.LabWarsBooster;
import dev.jade.labsaddons.mastery.MasteryQuestEntry;
import dev.jade.labsaddons.hud.HudObjectSettings;
import dev.jade.labsaddons.item.ItemUsesCorner;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Mod configuration persisted to {@code config/labsaddons.json}. Loaded lazily and
 * shared as a singleton across the renderer, sound hook, chum timer, and config
 * screen.
 */
public class LabsAddonsConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("labsaddons");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	/** The sections that get a file. {@link ConfigSection#LEGACY} deliberately does not. */
	private static final List<ConfigSection> WRITTEN = List.of(
			ConfigSection.SETTINGS, ConfigSection.STATE, ConfigSection.RUNNERS, ConfigSection.PROFILE);
	private static final Map<ConfigSection, Gson> SECTION_GSON = buildSectionGson();
	/** Last content written per section, so an unchanged section is not rewritten. */
	private static final Map<ConfigSection, String> LAST_WRITTEN = new EnumMap<>(ConfigSection.class);
	private static final Object SAVE_LOCK = new Object();

	/**
	 * Serialising {@code runnerStats} just to notice it is unchanged would cost megabytes
	 * on a save triggered by any other subsystem, so that one section is tracked by an
	 * explicit flag instead. Starts true so a fresh or migrated config writes it once.
	 */
	private static volatile boolean runnersDirty = true;

	private static ConfigStore store;

	/**
	 * Resolved lazily rather than in a static initialiser: touching {@code FabricLoader}
	 * at class-init is what made this class impossible to unit-test.
	 */
	private static ConfigStore store() {
		if (store == null) {
			store = new ConfigStore(FabricLoader.getInstance().getConfigDir());
		}
		return store;
	}

	/** The backing store, for the profile screen's file listing. */
	public static ConfigStore storage() {
		return store();
	}

	/** Points persistence at {@code configDir}. For tests only. */
	static void useStore(ConfigStore replacement) {
		store = replacement;
		instance = null;
		LAST_WRITTEN.clear();
		runnersDirty = true;
	}

	private static Map<ConfigSection, Gson> buildSectionGson() {
		Map<ConfigSection, Gson> bySection = new EnumMap<>(ConfigSection.class);
		for (ConfigSection section : WRITTEN) {
			bySection.put(section, new GsonBuilder().setPrettyPrinting()
					.addSerializationExclusionStrategy(new ExclusionStrategy() {
						@Override
						public boolean shouldSkipField(FieldAttributes field) {
							// Only this class's own fields are sectioned. Without this
							// guard the strategy also runs over nested types
							// (HudObjectSettings, RunnerStats, ...), whose unannotated
							// fields default to SETTINGS and would be stripped out of
							// every other section — writing {} for each entry.
							if (field.getDeclaringClass() != LabsAddonsConfig.class) {
								return false;
							}
							return sectionOf(field) != section;
						}

						@Override
						public boolean shouldSkipClass(Class<?> type) {
							return false;
						}
					}).create());
		}
		return bySection;
	}

	/** An unannotated field belongs to {@link ConfigSection#SETTINGS}; see {@link Section}. */
	private static ConfigSection sectionOf(FieldAttributes field) {
		Section marker = field.getAnnotation(Section.class);
		return marker == null ? ConfigSection.SETTINGS : marker.value();
	}

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
	@Section(ConfigSection.STATE)
	public long chumExpiryEpochMs = 0L;

	// --- Mini-events & Pit (tracked from chat announcements) ---
	@Section(ConfigSection.STATE)
	public String miniEventType = "";
	@Section(ConfigSection.STATE)
	public long miniEventExpiryEpochMs = 0L;
	@Section(ConfigSection.STATE)
	public long miniEventUpcomingEpochMs = 0L;
	@Section(ConfigSection.STATE)
	public long pitExpiryEpochMs = 0L;

	// --- Rental mount & personal boosts (tracked from chat + items) ---
	@Section(ConfigSection.STATE)
	public long rentalMountExpiryEpochMs = 0L;
	@Section(ConfigSection.STATE)
	public long personalChemPriceExpiryMs = 0L;
	@Section(ConfigSection.STATE)
	public long personalPrestigeExpiryMs = 0L;

	// --- Dailies & votes (reset at 9 PM Pacific; see daily.DailyReset) ---
	@Section(ConfigSection.STATE)
	public long dailySpinClaimedMs = 0L;
	@Section(ConfigSection.STATE)
	public long smClaimedMs = 0L;
	@Section(ConfigSection.STATE)
	public int voteCount = 0;
	@Section(ConfigSection.STATE)
	public long voteBoundaryMs = 0L;

	// --- Server boosters (tracked from chat announcements) ---
	@Section(ConfigSection.STATE)
	public java.util.Map<String, BoosterState> boosters = new java.util.LinkedHashMap<>();

	// --- Lab Wars revenue boosters (stack per category; tracked from chat + /lw rates GUI) ---
	@Section(ConfigSection.STATE)
	public java.util.List<LabWarsBooster> labWarsActive = new java.util.ArrayList<>();
	/** Legacy v1.6.x single-per-category map, migrated into labWarsActive on load. */
	@Section(ConfigSection.LEGACY)
	@Deprecated public java.util.Map<String, LabWarsBooster> labWarsBoosters;

	// --- Onboarding ---
	/** True once the first-run welcome guide has been shown in the HUD editor. */
	public boolean hasSeenWelcome = false;
	/** True once the one-time pre-rename keybind carry-over has run (v1.14.0). */
	public boolean keybindsMigrated = false;
	/** Whether the HUD editor's Widgets rail is rolled up, so it stays that way next open. */
	public boolean hudEditorRailCollapsed = false;

	// --- HUD profiles (layout sets, optionally auto-loaded per MCLabs world) ---
	/** Id of the profile whose layout is currently loaded; also names its file. */
	public String activeProfile = ConfigStore.DEFAULT_PROFILE;
	/**
	 * {@link dev.jade.labsaddons.server.McLabsWorld#id()} to profile id. A world with no
	 * entry keeps whichever profile is already loaded.
	 */
	public java.util.Map<String, String> worldProfiles = new java.util.LinkedHashMap<>();

	// --- Chemtainer contents (authoritative snapshot scraped from the /ch GUI) ---
	@Section(ConfigSection.STATE)
	public java.util.List<ChemtainerEntry> chemtainer = new java.util.ArrayList<>();
	/** When the contents above were last scraped (epoch ms); 0 = never opened. */
	@Section(ConfigSection.STATE)
	public long chemtainerSnapshotMs = 0L;
	/** Whether the player uses a satchel (changes the inventory-estimate divisor). */
	public boolean chemtainerSatchel = true;

	// --- Mastery challenges (progress widget) ---
	/** Last known board, restored on launch so chat reactions count before the first /mastery. */
	@Section(ConfigSection.STATE)
	public java.util.List<MasteryQuestEntry> masteryQuests = new java.util.ArrayList<>();
	/** When the board above was last scraped or advanced (epoch ms); 0 = never. */
	@Section(ConfigSection.STATE)
	public long masterySnapshotMs = 0L;

	// --- Chem prestige (progress widget) ---
	/** Last known tracks, restored on launch so a sale counts before the first sync. */
	@Section(ConfigSection.STATE)
	public java.util.List<dev.jade.labsaddons.prestige.PrestigeChemEntry> prestigeChems =
			new java.util.ArrayList<>();

	/**
	 * Row names (lowercased) kept on screen permanently — Mastery quest names and chem
	 * prestige names alike. Everything else only appears while it is gaining.
	 */
	@Section(ConfigSection.PROFILE)
	public java.util.Set<String> pinnedProgressRows = new java.util.LinkedHashSet<>();

	// --- Ability cooldowns widget (mcMMO + future cooldown sources) ---
	/** Stack cooldown rings in a column instead of a row. */
	@Section(ConfigSection.PROFILE)
	public boolean cooldownsStackVertical = false;
	/** Cooldown entry keys (e.g. "mcmmo:super_breaker") hidden from the widget. */
	@Section(ConfigSection.PROFILE)
	public java.util.Set<String> hiddenCooldownKeys = new java.util.LinkedHashSet<>();

	/** Raid Mine resource codes the player has switched off in the widget. */
	@Section(ConfigSection.PROFILE)
	public java.util.Set<String> hiddenRaidMineCodes = new java.util.LinkedHashSet<>();

	// --- HUD objects (position/scale/colors per widget id) ---
	@Section(ConfigSection.PROFILE)
	public java.util.Map<String, HudObjectSettings> hudObjects = new java.util.LinkedHashMap<>();

	// --- Runner leaderboard (per-runner all-time stats; supplier side) ---
	@Section(ConfigSection.RUNNERS)
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
	@Section(ConfigSection.LEGACY)
	@Deprecated public Boolean chumTimerEnabled;
	@Section(ConfigSection.LEGACY)
	@Deprecated public Float chumHudX;
	@Section(ConfigSection.LEGACY)
	@Deprecated public Float chumHudY;

	public static LabsAddonsConfig get() {
		if (instance == null) {
			instance = load(null);
		}
		return instance;
	}

	/**
	 * Swaps the active HUD profile: flushes the one being left under its own id, then
	 * reloads so every {@code hudObjects} reader sees the new layout without any of the
	 * ~12 call sites needing to know profiles exist.
	 */
	public static void activateProfile(String profileId) {
		String target = ConfigStore.safeProfileId(profileId);
		LabsAddonsConfig current = get();
		if (target.equals(current.activeProfile)) {
			return;
		}
		// Must land before activeProfile changes, or the outgoing layout is written
		// into the incoming profile's file.
		current.saveNow();
		instance = load(target);
		LOGGER.info("[labsaddons] HUD profile is now \"{}\".", target);
	}

	/**
	 * Copies the current layout into a new profile and makes it active. Creating by
	 * duplication is the useful default — a brand new profile would drop every widget
	 * back to its factory position.
	 */
	public static void duplicateActiveProfile(String newProfileId) {
		String target = ConfigStore.safeProfileId(newProfileId);
		LabsAddonsConfig current = get();
		current.activeProfile = target;
		// The in-memory layout is unchanged, so saving now writes it to the new file.
		LAST_WRITTEN.remove(ConfigSection.PROFILE);
		current.saveNow();
	}

	/**
	 * Renames the active profile in place, moving its file and repointing any world
	 * bindings. {@link ConfigStore#DEFAULT_PROFILE} is refused: it is the fallback
	 * loaded whenever a profile file is missing, so it has to keep existing.
	 *
	 * @return true if the rename happened
	 */
	public static boolean renameActiveProfile(String newProfileId) {
		String target = ConfigStore.safeProfileId(newProfileId);
		LabsAddonsConfig current = get();
		String previous = current.activeProfile;
		if (target.equals(previous) || ConfigStore.DEFAULT_PROFILE.equals(previous)
				|| store().profileExists(target)) {
			return false;
		}
		current.activeProfile = target;
		current.worldProfiles.replaceAll((world, profile) ->
				previous.equals(profile) ? target : profile);
		// Write the layout under its new name before dropping the old file, so a
		// failure here loses nothing.
		LAST_WRITTEN.remove(ConfigSection.PROFILE);
		current.saveNow();
		store().deleteProfile(previous);
		LOGGER.info("[labsaddons] Renamed HUD profile \"{}\" to \"{}\".", previous, target);
		return true;
	}

	private static LabsAddonsConfig load(String profileOverride) {
		ConfigStore configStore = store();
		LAST_WRITTEN.clear();
		JsonObject merged = configStore.loadMerged(profileOverride);

		LabsAddonsConfig loaded = null;
		try {
			loaded = GSON.fromJson(merged, LabsAddonsConfig.class);
		} catch (JsonParseException e) {
			LOGGER.warn("[labsaddons] Config under {} did not parse; using defaults.",
					configStore.root(), e);
		}
		LabsAddonsConfig clean = loaded == null ? new LabsAddonsConfig() : loaded.sanitized();

		// A missing section means a fresh install, a folder migration, or a file the
		// player deleted. Writing every section once is what actually splits a
		// migrated config into its files.
		if (configStore.sectionsWereMissing()) {
			runnersDirty = true;
			clean.persist(true);
		}
		return clean;
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
		clean.hudEditorRailCollapsed = this.hudEditorRailCollapsed;
		clean.activeProfile = ConfigStore.safeProfileId(this.activeProfile);
		if (this.worldProfiles != null) {
			this.worldProfiles.forEach((world, profile) -> {
				// Drop bindings for worlds the mod no longer knows, so a renamed or
				// retired world cannot leave an entry the screen can never show.
				if (profile != null && dev.jade.labsaddons.server.McLabsWorld.byId(world) != null) {
					clean.worldProfiles.put(world, ConfigStore.safeProfileId(profile));
				}
			});
		}
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
		clean.runnerAlarmThreshold = Math.clamp(this.runnerAlarmThreshold, 0,
				dev.jade.labsaddons.runner.RunnerAlarm.MAX_THRESHOLD);
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

	/** Persists any section whose content changed. Disk I/O is coalesced onto a background thread. */
	public void save() {
		persist(false);
	}

	/**
	 * Kept as a distinct name only because 14 call sites use it. Identical to
	 * {@link #save()} now that every save is off-thread.
	 */
	public void saveAsync() {
		persist(false);
	}

	/** Persists and waits for the write to land. Use on disconnect, editor close and shutdown. */
	public void saveNow() {
		persist(true);
	}

	/** Marks the runner leaderboard as needing a write. See {@link #runnersDirty}. */
	public static void markRunnersDirty() {
		runnersDirty = true;
	}

	private void persist(boolean waitForDisk) {
		ConfigStore configStore = store();
		// Serialising here keeps it on the caller's (game) thread, so a background
		// writer can never walk a map the game is mutating.
		synchronized (SAVE_LOCK) {
			for (ConfigSection section : WRITTEN) {
				if (section == ConfigSection.RUNNERS && !runnersDirty) {
					continue;
				}
				String json = render(section);
				if (section == ConfigSection.RUNNERS) {
					runnersDirty = false;
				}
				if (json.equals(LAST_WRITTEN.get(section))) {
					continue;
				}
				LAST_WRITTEN.put(section, json);
				configStore.queue(configStore.fileFor(section, this.activeProfile), json);
			}
		}
		if (waitForDisk) {
			configStore.flushNow();
		}
	}

	/** This config as the JSON for one section, format-stamped. */
	private String render(ConfigSection section) {
		JsonObject body = SECTION_GSON.get(section).toJsonTree(this).getAsJsonObject();
		JsonObject out = new JsonObject();
		out.addProperty(ConfigStore.FORMAT_KEY, ConfigStore.FORMAT_VERSION);
		body.entrySet().forEach(entry -> out.add(entry.getKey(), entry.getValue()));
		return GSON.toJson(out);
	}
}
