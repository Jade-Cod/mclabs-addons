package dev.jade.labsaddons.config;

import com.google.gson.JsonObject;
import dev.jade.labsaddons.hud.HudObjectSettings;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the field-to-file mapping. A field that silently lands in the wrong section
 * would be written to a file nothing reads back, so this checks the split by
 * reflection rather than by listing field names a second time.
 */
public class ConfigSectionSplitTest {
	@TempDir
	Path configDir;

	@AfterEach
	public void resetStore() {
		LabsAddonsConfig.useStore(null);
	}

	private Path folder() {
		return configDir.resolve(ConfigStore.FOLDER);
	}

	private JsonObject read(Path path) throws IOException {
		return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
	}

	/** Every persisted field, paired with the section it is annotated for. */
	private static List<Field> persistedFields() {
		List<Field> fields = new ArrayList<>();
		for (Field field : LabsAddonsConfig.class.getDeclaredFields()) {
			if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
				fields.add(field);
			}
		}
		return fields;
	}

	private static ConfigSection sectionOf(Field field) {
		Section marker = field.getAnnotation(Section.class);
		return marker == null ? ConfigSection.SETTINGS : marker.value();
	}

	@Test
	public void everyPersistedFieldIsWrittenToExactlyOneFile() throws IOException {
		LabsAddonsConfig.useStore(new ConfigStore(configDir));
		LabsAddonsConfig.get().saveNow();

		JsonObject settings = read(folder().resolve("settings.json"));
		JsonObject state = read(folder().resolve("state.json"));
		JsonObject runners = read(folder().resolve("runners.json"));
		JsonObject profile = read(folder().resolve("profiles").resolve("default.json"));

		for (Field field : persistedFields()) {
			String name = field.getName();
			ConfigSection section = sectionOf(field);
			int seen = 0;
			seen += settings.has(name) ? 1 : 0;
			seen += state.has(name) ? 1 : 0;
			seen += runners.has(name) ? 1 : 0;
			seen += profile.has(name) ? 1 : 0;

			if (section == ConfigSection.LEGACY) {
				assertEquals(0, seen, name + " is legacy and must not be written to any file");
				continue;
			}
			// A null-valued field is simply omitted by Gson, which is fine; what must
			// never happen is the same field landing in two files.
			assertTrue(seen <= 1, name + " was written to more than one file");
			switch (section) {
				case SETTINGS -> assertTrue(settings.has(name) || seen == 0, name + " belongs in settings.json");
				case STATE -> assertTrue(state.has(name) || seen == 0, name + " belongs in state.json");
				case RUNNERS -> assertTrue(runners.has(name) || seen == 0, name + " belongs in runners.json");
				case PROFILE -> assertTrue(profile.has(name) || seen == 0, name + " belongs in the profile file");
				default -> throw new AssertionError(section);
			}
		}
	}

	/**
	 * Regression: the section exclusion strategy must not reach inside nested types.
	 * When it did, every widget's settings serialised as {} and a migrating player
	 * lost their whole layout and leaderboard.
	 */
	@Test
	public void nestedObjectsKeepTheirFieldsInEverySection() throws IOException {
		LabsAddonsConfig.useStore(new ConfigStore(configDir));
		LabsAddonsConfig config = LabsAddonsConfig.get();

		HudObjectSettings widget = new HudObjectSettings();
		widget.x = 0.31f;
		widget.y = 0.62f;
		widget.scale = 1.5f;
		widget.enabled = false;
		config.hudObjects.put("chum_timer", widget);

		RunnerStats stats = new RunnerStats();
		stats.completed = 12;
		stats.valueSold = 4200.0;
		stats.recentJobs.add(new RunnerJob("Cactatonate-2-2-2", 2176, 22300.0, 1786847126515L, 63970L));
		config.runnerStats.put("coreboy95", stats);
		LabsAddonsConfig.markRunnersDirty();
		config.saveNow();

		JsonObject profile = read(folder().resolve("profiles").resolve("default.json"));
		JsonObject saved = profile.getAsJsonObject("hudObjects").getAsJsonObject("chum_timer");
		assertEquals(0.31, saved.get("x").getAsDouble(), 0.0001, "nested widget fields were stripped");
		assertEquals(1.5, saved.get("scale").getAsDouble(), 0.0001);

		JsonObject runner = read(folder().resolve("runners.json"))
				.getAsJsonObject("runnerStats").getAsJsonObject("coreboy95");
		assertEquals(12, runner.get("completed").getAsInt(), "nested runner fields were stripped");
		assertEquals(1, runner.getAsJsonArray("recentJobs").size());
		assertEquals(2176, runner.getAsJsonArray("recentJobs").get(0)
				.getAsJsonObject().get("qty").getAsInt());

		// and it all comes back
		LabsAddonsConfig.useStore(new ConfigStore(configDir));
		LabsAddonsConfig reloaded = LabsAddonsConfig.get();
		assertEquals(0.31f, reloaded.hudObjects.get("chum_timer").x, 0.0001f);
		assertFalse(reloaded.hudObjects.get("chum_timer").enabled);
		assertEquals(12, reloaded.runnerStats.get("coreboy95").completed);
		assertEquals(1, reloaded.runnerStats.get("coreboy95").recentJobs.size());
	}

	@Test
	public void everyFileCarriesAFormatStamp() throws IOException {
		LabsAddonsConfig.useStore(new ConfigStore(configDir));
		LabsAddonsConfig.get().saveNow();

		for (Path path : List.of(folder().resolve("settings.json"), folder().resolve("state.json"),
				folder().resolve("runners.json"), folder().resolve("profiles").resolve("default.json"))) {
			assertEquals(ConfigStore.FORMAT_VERSION,
					read(path).get(ConfigStore.FORMAT_KEY).getAsInt(), path + " has no format stamp");
		}
	}

	@Test
	public void aFlatConfigMigratesIntoTheRightFiles() throws IOException {
		Files.writeString(configDir.resolve("labsaddons.json"), """
				{
				  "markerScale": 1.75,
				  "voteCount": 4,
				  "cooldownsStackVertical": true,
				  "hudObjects": { "chum_timer": { "enabled": false, "x": 0.5, "y": 0.5 } }
				}
				""");
		LabsAddonsConfig.useStore(new ConfigStore(configDir));

		LabsAddonsConfig config = LabsAddonsConfig.get();

		// values survived
		assertEquals(1.75f, config.markerScale, 0.0001f);
		assertEquals(4, config.voteCount);
		assertTrue(config.cooldownsStackVertical);
		assertFalse(config.hudObjects.get("chum_timer").enabled);

		// and landed in the right files
		assertTrue(read(folder().resolve("settings.json")).has("markerScale"));
		assertTrue(read(folder().resolve("state.json")).has("voteCount"));
		JsonObject profile = read(folder().resolve("profiles").resolve("default.json"));
		assertTrue(profile.has("hudObjects"));
		assertTrue(profile.get("cooldownsStackVertical").getAsBoolean());

		assertFalse(read(folder().resolve("settings.json")).has("voteCount"),
				"state must not leak into settings.json");
		assertTrue(Files.exists(configDir.resolve("labsaddons.json")), "the old file stays as a backup");
	}

	@Test
	public void aSavedConfigReloadsToTheSameValues() throws IOException {
		LabsAddonsConfig.useStore(new ConfigStore(configDir));
		LabsAddonsConfig config = LabsAddonsConfig.get();
		config.markerScale = 2.5f;
		config.voteCount = 9;
		config.activeProfile = "fishing";
		config.worldProfiles.put("underworld", "fishing");
		config.cooldownsStackVertical = true;
		config.saveNow();

		LabsAddonsConfig.useStore(new ConfigStore(configDir));
		LabsAddonsConfig reloaded = LabsAddonsConfig.get();

		assertEquals(2.5f, reloaded.markerScale, 0.0001f);
		assertEquals(9, reloaded.voteCount);
		assertEquals("fishing", reloaded.activeProfile);
		assertEquals("fishing", reloaded.worldProfiles.get("underworld"));
		assertTrue(reloaded.cooldownsStackVertical, "the layout came back from profiles/fishing.json");
	}

	@Test
	public void twoProfilesKeepSeparateLayoutsButShareStateAndSettings() throws IOException {
		LabsAddonsConfig.useStore(new ConfigStore(configDir));
		LabsAddonsConfig config = LabsAddonsConfig.get();
		config.markerScale = 3.0f;
		config.voteCount = 5;
		config.cooldownsStackVertical = true;
		config.saveNow();

		config.activeProfile = "mining";
		config.cooldownsStackVertical = false;
		config.saveNow();

		LabsAddonsConfig.useStore(new ConfigStore(configDir));
		LabsAddonsConfig mining = LabsAddonsConfig.get();
		assertEquals("mining", mining.activeProfile);
		assertFalse(mining.cooldownsStackVertical);
		assertEquals(3.0f, mining.markerScale, 0.0001f, "settings are shared across profiles");
		assertEquals(5, mining.voteCount, "server state is shared across profiles");

		JsonObject defaultProfile = read(folder().resolve("profiles").resolve("default.json"));
		assertTrue(defaultProfile.get("cooldownsStackVertical").getAsBoolean(),
				"switching profiles must not overwrite the one you left");
	}

	@Test
	public void renamingAProfileMovesItsFileAndItsWorldBindings() throws IOException {
		LabsAddonsConfig.useStore(new ConfigStore(configDir));
		LabsAddonsConfig config = LabsAddonsConfig.get();
		LabsAddonsConfig.duplicateActiveProfile("profile2");
		config.cooldownsStackVertical = true;
		config.worldProfiles.put("underworld", "profile2");
		config.saveNow();

		assertTrue(LabsAddonsConfig.renameActiveProfile("mining"));

		assertEquals("mining", config.activeProfile);
		assertEquals("mining", config.worldProfiles.get("underworld"), "the binding followed it");
		assertTrue(Files.exists(folder().resolve("profiles").resolve("mining.json")));
		assertFalse(Files.exists(folder().resolve("profiles").resolve("profile2.json")),
				"the old file is gone, not left as a duplicate");

		LabsAddonsConfig.useStore(new ConfigStore(configDir));
		assertTrue(LabsAddonsConfig.get().cooldownsStackVertical, "the layout came with it");
	}

	@Test
	public void theDefaultProfileCannotBeRenamedAway() {
		LabsAddonsConfig.useStore(new ConfigStore(configDir));
		LabsAddonsConfig config = LabsAddonsConfig.get();

		assertFalse(LabsAddonsConfig.renameActiveProfile("mine"),
				"default is the fallback when a profile file is missing");
		assertEquals("default", config.activeProfile);
	}

	@Test
	public void renamingOntoAnExistingProfileIsRefused() {
		LabsAddonsConfig.useStore(new ConfigStore(configDir));
		LabsAddonsConfig.duplicateActiveProfile("fishing");
		LabsAddonsConfig.duplicateActiveProfile("mining");

		assertFalse(LabsAddonsConfig.renameActiveProfile("fishing"),
				"renaming onto a live profile would overwrite its layout");
		assertEquals("mining", LabsAddonsConfig.get().activeProfile);
	}

	@Test
	public void aCorruptRunnersFileCostsOnlyTheLeaderboard() throws IOException {
		LabsAddonsConfig.useStore(new ConfigStore(configDir));
		LabsAddonsConfig config = LabsAddonsConfig.get();
		config.markerScale = 1.5f;
		config.runnerStats.put("Spleaf", new RunnerStats());
		LabsAddonsConfig.markRunnersDirty();
		config.saveNow();

		Files.writeString(folder().resolve("runners.json"), "{ truncated");

		LabsAddonsConfig.useStore(new ConfigStore(configDir));
		LabsAddonsConfig reloaded = LabsAddonsConfig.get();

		assertEquals(1.5f, reloaded.markerScale, 0.0001f, "settings survived");
		assertTrue(reloaded.runnerStats.isEmpty(), "the leaderboard is what was lost");
	}

	@Test
	public void theLeaderboardIsOnlyRewrittenWhenItIsMarkedDirty() throws IOException {
		LabsAddonsConfig.useStore(new ConfigStore(configDir));
		LabsAddonsConfig config = LabsAddonsConfig.get();
		config.saveNow();

		Path runners = folder().resolve("runners.json");
		Files.writeString(runners, "{\"sentinel\": true}");

		// A save triggered by an unrelated subsystem must not touch runners.json.
		config.voteCount = 2;
		config.saveNow();
		assertTrue(read(runners).has("sentinel"), "an unrelated save rewrote the leaderboard");

		config.runnerStats.put("Spleaf", new RunnerStats());
		LabsAddonsConfig.markRunnersDirty();
		config.saveNow();
		assertFalse(read(runners).has("sentinel"), "a dirty leaderboard must be written");
	}

	@Test
	public void aLegacyFieldIsReadOnceAndNeverWrittenBack() throws IOException {
		Files.writeString(configDir.resolve("labsaddons.json"),
				"{\"chumTimerEnabled\": false, \"chumHudX\": 0.25, \"chumHudY\": 0.75}");
		LabsAddonsConfig.useStore(new ConfigStore(configDir));

		LabsAddonsConfig config = LabsAddonsConfig.get();
		// sanitized() folds the v1.2.x fields into the generic widget map...
		assertFalse(config.hudObjects.get("chum_timer").enabled);
		assertEquals(0.25f, config.hudObjects.get("chum_timer").x, 0.0001f);

		// ...and they are gone from disk.
		for (Path path : List.of(folder().resolve("settings.json"), folder().resolve("state.json"),
				folder().resolve("runners.json"), folder().resolve("profiles").resolve("default.json"))) {
			assertFalse(read(path).has("chumTimerEnabled"), path + " still carries a legacy field");
		}
	}
}
