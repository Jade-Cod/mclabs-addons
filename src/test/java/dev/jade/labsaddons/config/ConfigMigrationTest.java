package dev.jade.labsaddons.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigMigrationTest {
	@TempDir
	Path configDir;

	@Test
	public void copiesLegacyFileWhenNewFileAbsent() throws IOException {
		Path legacy = configDir.resolve("fishbite.json");
		Path current = configDir.resolve("labsaddons.json");
		Files.writeString(legacy, "{\"enabled\": true, \"markerScale\": 1.5}");

		ConfigMigration.migrate(current, legacy);

		assertTrue(Files.exists(current));
		assertEquals(Files.readString(legacy), Files.readString(current));
		assertTrue(Files.exists(legacy), "legacy file must stay in place as a backup");
	}

	@Test
	public void neverOverwritesExistingNewFile() throws IOException {
		Path legacy = configDir.resolve("fishbite.json");
		Path current = configDir.resolve("labsaddons.json");
		Files.writeString(legacy, "{\"enabled\": false}");
		Files.writeString(current, "{\"enabled\": true}");

		ConfigMigration.migrate(current, legacy);

		assertEquals("{\"enabled\": true}", Files.readString(current));
	}

	@Test
	public void noOpWhenNeitherFileExists() {
		Path legacy = configDir.resolve("fishbite.json");
		Path current = configDir.resolve("labsaddons.json");

		ConfigMigration.migrate(current, legacy);

		assertFalse(Files.exists(current));
		assertFalse(Files.exists(legacy));
	}

	@Test
	public void createsMissingParentDirectories() throws IOException {
		Path legacy = configDir.resolve("fishbite.json");
		Path current = configDir.resolve("nested/config/labsaddons.json");
		Files.writeString(legacy, "{}");

		ConfigMigration.migrate(current, legacy);

		assertTrue(Files.exists(current));
	}
}
