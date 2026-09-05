package dev.jade.labsaddons.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Reads and writes the config files under {@code config/labsaddons/}.
 *
 * <p>Deliberately takes its base directory as a constructor argument rather than
 * resolving {@code FabricLoader} itself — a deliberate choice that is
 * what lets this class (and the folder migration, the riskiest part of
 * the split) can be unit-tested against a {@code @TempDir}.
 *
 * <p>Writes are atomic: content goes to a sibling {@code .tmp} file which is then
 * moved over the target, so a crash mid-write can never leave a half-written config.
 * They are also coalesced onto a background thread, because the callers include
 * {@code END_CLIENT_TICK} handlers that fire on every mob kill.
 */
public final class ConfigStore {
	private static final Logger LOGGER = LoggerFactory.getLogger("labsaddons");

	static final int FORMAT_VERSION = 1;
	/** Written into every file and stripped on read, so a future schema change has something to branch on. */
	static final String FORMAT_KEY = "formatVersion";
	public static final String DEFAULT_PROFILE = "default";
	/** Folder holding the split files, inside the game's config directory. */
	static final String FOLDER = "labsaddons";

	/**
	 * A profile id becomes a file name, so it is validated rather than trusted.
	 * Anything else falls back to {@link #DEFAULT_PROFILE}.
	 */
	private static final Pattern SAFE_PROFILE_ID = Pattern.compile("[a-z0-9_-]{1,32}");
	/** How long a burst of saves is gathered before one disk write. */
	private static final long COALESCE_MS = 250L;

	private final Path configDir;
	private final Path root;
	private final Path profilesDir;

	/** Latest content per target file, replaced in place so a burst writes once. */
	private final Map<Path, String> pending = new ConcurrentHashMap<>();
	private final AtomicBoolean flushScheduled = new AtomicBoolean();
	/** Set by {@link #loadMerged} when any section file was absent or unreadable. */
	private volatile boolean sectionsMissing;
	private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "labsaddons-config-writer");
		thread.setDaemon(true);
		return thread;
	});

	public ConfigStore(Path configDir) {
		this.configDir = configDir;
		this.root = configDir.resolve(FOLDER);
		this.profilesDir = root.resolve("profiles");
	}

	public Path root() {
		return root;
	}

	/**
	 * Derives a profile id from text a player typed. Lowercases, turns spaces into
	 * dashes and drops anything that is not id-safe, so "My Fishing Setup" becomes
	 * "my-fishing-setup" rather than being rejected.
	 *
	 * @return the derived id, or "" when nothing usable is left
	 */
	public static String toProfileId(String typed) {
		if (typed == null) {
			return "";
		}
		StringBuilder derived = new StringBuilder();
		for (char c : typed.trim().toLowerCase(Locale.ROOT).toCharArray()) {
			if (Character.isLetterOrDigit(c) && c < 128) {
				derived.append(c);
			} else if (c == ' ' || c == '-' || c == '_') {
				derived.append(c == ' ' ? '-' : c);
			}
			if (derived.length() == 32) {
				break;
			}
		}
		// A name still being typed ends in a separator ("my fishing " -> "my-fishing-");
		// nobody wants that saved as the id.
		while (derived.length() > 0 && isSeparator(derived.charAt(0))) {
			derived.deleteCharAt(0);
		}
		while (derived.length() > 0 && isSeparator(derived.charAt(derived.length() - 1))) {
			derived.setLength(derived.length() - 1);
		}
		return derived.toString();
	}

	private static boolean isSeparator(char c) {
		return c == '-' || c == '_';
	}

	/** Sanitises an id into one that is safe to use as a file name. */
	public static String safeProfileId(String id) {
		if (id == null) {
			return DEFAULT_PROFILE;
		}
		String lower = id.trim().toLowerCase(Locale.ROOT);
		return SAFE_PROFILE_ID.matcher(lower).matches() ? lower : DEFAULT_PROFILE;
	}

	/** Target file for a section; {@code profileId} is only consulted for {@link ConfigSection#PROFILE}. */
	public Path fileFor(ConfigSection section, String profileId) {
		if (section == ConfigSection.PROFILE) {
			return profilesDir.resolve(safeProfileId(profileId) + ".json");
		}
		String name = section.fileName();
		return name == null ? null : root.resolve(name);
	}

	/** Profile ids that have a file on disk, for the profile picker. */
	public java.util.List<String> listProfiles() {
		java.util.List<String> ids = new java.util.ArrayList<>();
		if (!Files.isDirectory(profilesDir)) {
			return ids;
		}
		try (java.util.stream.Stream<Path> files = Files.list(profilesDir)) {
			files.filter(p -> p.getFileName().toString().endsWith(".json"))
					.map(p -> p.getFileName().toString())
					.map(n -> n.substring(0, n.length() - ".json".length()))
					.sorted()
					.forEach(ids::add);
		} catch (IOException e) {
			LOGGER.warn("[labsaddons] Could not list profiles in {}.", profilesDir, e);
		}
		return ids;
	}

	public boolean profileExists(String profileId) {
		return Files.exists(fileFor(ConfigSection.PROFILE, profileId));
	}

	public void deleteProfile(String profileId) {
		Path path = fileFor(ConfigSection.PROFILE, profileId);
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			LOGGER.warn("[labsaddons] Could not delete profile {}.", path, e);
		}
	}

	/**
	 * Reads every section into one object, which is exactly the shape the old flat
	 * {@code labsaddons.json} had — so the caller's existing Gson binding and
	 * {@code sanitized()} need no changes.
	 *
	 * <p>The pre-split file, when there is one, goes in underneath as a base layer;
	 * that is the whole folder migration. Returns an empty object when nothing exists
	 * yet, which deserialises to a defaults instance.
	 *
	 * @param profileOverride profile to load instead of the one named in settings, or null
	 */
	public JsonObject loadMerged(String profileOverride) {
		sectionsMissing = false;
		JsonObject merged = new JsonObject();
		mergeInto(merged, ConfigFolderMigration.legacyBase(this, configDir));

		JsonObject settings = readTracked(fileFor(ConfigSection.SETTINGS, null));
		mergeInto(merged, settings);
		mergeInto(merged, readTracked(fileFor(ConfigSection.STATE, null)));
		mergeInto(merged, readTracked(fileFor(ConfigSection.RUNNERS, null)));

		String profile = profileOverride;
		if (profile == null && settings != null && settings.has("activeProfile")) {
			JsonElement active = settings.get("activeProfile");
			profile = active.isJsonPrimitive() ? active.getAsString() : null;
		}
		profile = safeProfileId(profile);
		mergeInto(merged, readTracked(fileFor(ConfigSection.PROFILE, profile)));
		merged.addProperty("activeProfile", profile);
		return merged;
	}

	/**
	 * True when the last {@link #loadMerged} found a section file missing — a fresh
	 * install, a folder migration, or a file the player deleted. The caller answers it
	 * by writing every section once, which is what actually splits a migrated config.
	 */
	public boolean sectionsWereMissing() {
		return sectionsMissing;
	}

	private JsonObject readTracked(Path path) {
		JsonObject loaded = readObject(path);
		if (loaded == null) {
			sectionsMissing = true;
		}
		return loaded;
	}

	/** Copies every key of {@code source} into {@code target}, dropping the format stamp. */
	private static void mergeInto(JsonObject target, JsonObject source) {
		if (source == null) {
			return;
		}
		for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
			if (!FORMAT_KEY.equals(entry.getKey())) {
				target.add(entry.getKey(), entry.getValue());
			}
		}
	}

	/**
	 * Parses one file. A missing file is normal; a corrupt one is reported and treated
	 * as absent, so a broken {@code runners.json} costs the leaderboard and not the
	 * player's whole layout.
	 */
	JsonObject readObject(Path path) {
		if (path == null || !Files.exists(path)) {
			return null;
		}
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(path));
			if (parsed != null && parsed.isJsonObject()) {
				return parsed.getAsJsonObject();
			}
			LOGGER.warn("[labsaddons] {} is not a JSON object; ignoring it.", path);
		} catch (IOException | JsonParseException e) {
			LOGGER.warn("[labsaddons] Could not read {}; continuing without it.", path, e);
		}
		return null;
	}

	/** Queues {@code json} for {@code path}, replacing anything already queued for it. */
	public void queue(Path path, String json) {
		if (path == null) {
			return;
		}
		pending.put(path, json);
		scheduleFlush();
	}

	private void scheduleFlush() {
		if (!flushScheduled.compareAndSet(false, true)) {
			return;
		}
		writer.execute(() -> {
			try {
				Thread.sleep(COALESCE_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			// Cleared before draining so a save arriving mid-flush schedules another
			// pass rather than being dropped.
			flushScheduled.set(false);
			drain();
		});
	}

	/** Writes everything queued, now, on the calling thread. */
	public void flushNow() {
		drain();
	}

	private synchronized void drain() {
		for (Path path : pending.keySet().toArray(new Path[0])) {
			String json = pending.remove(path);
			if (json != null) {
				writeAtomic(path, json);
			}
		}
	}

	/** Writes via a temp file and a move, so the target is never seen half-written. */
	void writeAtomic(Path path, String json) {
		Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(tmp, json);
			try {
				Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				// Some filesystems (a few network mounts) refuse atomic moves; a plain
				// replace is still better than truncating the live file in place.
				Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			LOGGER.warn("[labsaddons] Failed to write {}.", path, e);
			try {
				Files.deleteIfExists(tmp);
			} catch (IOException suppressed) {
				LOGGER.warn("[labsaddons] Also failed to clean up {}.", tmp, suppressed);
			}
		}
	}
}
