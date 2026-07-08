package dev.jade.labsaddons.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One-time carry-over of the config file from the mod's pre-rename id
 * (v1.13.x and older wrote {@code config/fishbite.json}). The JSON schema is
 * identical — only the filename changed — so a straight copy is the whole
 * migration. The old file is left in place as a backup.
 */
final class ConfigMigration {
	private static final Logger LOGGER = LoggerFactory.getLogger("labsaddons");

	private ConfigMigration() {
	}

	/** Copies {@code legacyPath} to {@code configPath} iff the new file is absent. */
	static void migrate(Path configPath, Path legacyPath) {
		if (Files.exists(configPath) || !Files.exists(legacyPath)) {
			return;
		}
		try {
			Files.createDirectories(configPath.getParent());
			Files.copy(legacyPath, configPath);
			LOGGER.info("[labsaddons] Migrated config from {} to {}.", legacyPath, configPath);
		} catch (IOException e) {
			LOGGER.warn("[labsaddons] Failed to migrate legacy config from {}.", legacyPath, e);
		}
	}
}
