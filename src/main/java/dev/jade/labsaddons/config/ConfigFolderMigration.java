package dev.jade.labsaddons.config;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * One-time carry-over from the single flat config file (v1.15.x and older wrote
 * {@code config/labsaddons.json}) to the sectioned folder {@code config/labsaddons/}.
 *
 * <p>The flat file's shape is exactly the shape {@link ConfigStore#loadMerged} builds
 * by merging the section files, so the whole migration is "use the old file as the
 * base layer and let the first save split it". No field-by-field transform, and
 * {@link LabsAddonsConfig#sanitized()} keeps handling schema changes as it always has.
 *
 * <p>Follows {@link ConfigMigration}: guarded by existence, best-effort, and the old
 * file is left untouched on disk as a backup.
 */
final class ConfigFolderMigration {
	private static final Logger LOGGER = LoggerFactory.getLogger("labsaddons");

	/** Pre-split file names, newest first. */
	private static final List<String> LEGACY_FILES = List.of("labsaddons.json", "fishbite.json");

	private ConfigFolderMigration() {
	}

	/**
	 * @return the pre-split config to use as a base layer, or null when the folder
	 *         already exists (nothing to migrate) or no old file is present.
	 */
	static JsonObject legacyBase(ConfigStore store, Path configDir) {
		if (Files.exists(store.root())) {
			return null;
		}
		for (String name : LEGACY_FILES) {
			Path legacy = configDir.resolve(name);
			JsonObject loaded = store.readObject(legacy);
			if (loaded != null) {
				LOGGER.info("[labsaddons] Migrating {} into {}; the old file is left as a backup.",
						legacy, store.root());
				return loaded;
			}
		}
		return null;
	}
}
