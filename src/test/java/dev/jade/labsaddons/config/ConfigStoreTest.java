package dev.jade.labsaddons.config;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the storage layer against a temp directory. This is only possible because
 * {@link ConfigStore} takes its base path as an argument instead of asking FabricLoader.
 */
public class ConfigStoreTest {
	@TempDir
	Path configDir;

	private ConfigStore store() {
		return new ConfigStore(configDir);
	}

	private Path folder() {
		return configDir.resolve(ConfigStore.FOLDER);
	}

	// --- profile ids become file names, so they are validated, not trusted ---

	@Test
	public void aProfileIdThatWouldEscapeTheFolderFallsBackToDefault() {
		assertEquals("default", ConfigStore.safeProfileId("../../etc/passwd"));
		assertEquals("default", ConfigStore.safeProfileId("a/b"));
		assertEquals("default", ConfigStore.safeProfileId(".."));
		assertEquals("default", ConfigStore.safeProfileId(""));
		assertEquals("default", ConfigStore.safeProfileId(null));
		assertEquals("default", ConfigStore.safeProfileId("x".repeat(33)));
	}

	@Test
	public void ordinaryProfileIdsAreKeptAndLowercased() {
		assertEquals("fishing", ConfigStore.safeProfileId("Fishing"));
		assertEquals("under_world-2", ConfigStore.safeProfileId("  Under_World-2 "));
	}

	// --- typed names become ids ---

	@Test
	public void aTypedNameBecomesAUsableId() {
		assertEquals("my-fishing-setup", ConfigStore.toProfileId("My Fishing Setup"));
		assertEquals("pit", ConfigStore.toProfileId("  Pit  "));
		assertEquals("under_world-2", ConfigStore.toProfileId("Under_World 2"));
	}

	@Test
	public void aHalfTypedNameDoesNotKeepItsTrailingSeparator() {
		assertEquals("my-fishing", ConfigStore.toProfileId("My Fishing "));
		assertEquals("fishing", ConfigStore.toProfileId("--fishing--"));
		assertEquals("", ConfigStore.toProfileId("---"));
	}

	@Test
	public void toProfileIdIsIdempotent() {
		String once = ConfigStore.toProfileId("My Fishing Setup ");
		assertEquals(once, ConfigStore.toProfileId(once),
				"the name field re-feeds its own output, so this must settle in one pass");
	}

	@Test
	public void aTypedNameWithNothingUsableInItYieldsNoId() {
		assertEquals("", ConfigStore.toProfileId("   "));
		assertEquals("", ConfigStore.toProfileId(null));
		assertEquals("", ConfigStore.toProfileId("!!!"));
	}

	@Test
	public void aTypedNameIsTruncatedRatherThanRejected() {
		String derived = ConfigStore.toProfileId("x".repeat(80));
		assertEquals(32, derived.length());
		assertEquals(derived, ConfigStore.safeProfileId(derived),
				"whatever toProfileId produces must survive safeProfileId");
	}

	@Test
	public void aProfileFileLivesUnderTheProfilesFolder() {
		Path path = store().fileFor(ConfigSection.PROFILE, "fishing");
		assertEquals(folder().resolve("profiles").resolve("fishing.json"), path);
	}

	// --- reads are best-effort and never throw ---

	@Test
	public void aCorruptSectionIsTreatedAsAbsentRatherThanFatal() throws IOException {
		Files.createDirectories(folder());
		Files.writeString(folder().resolve("settings.json"), "{\"markerScale\": 1.5}");
		Files.writeString(folder().resolve("runners.json"), "{ this is not json");

		JsonObject merged = store().loadMerged(null);

		assertEquals(1.5, merged.get("markerScale").getAsDouble(), 0.0001,
				"a broken runners.json must not cost the player their settings");
		assertFalse(merged.has("runnerStats"));
	}

	@Test
	public void aMissingFolderLoadsAsEmptyRatherThanFailing() {
		ConfigStore store = store();
		JsonObject merged = store.loadMerged(null);
		assertNotNull(merged);
		assertTrue(store.sectionsWereMissing());
	}

	// --- writes ---

	@Test
	public void writesAreAtomicAndLeaveNoTempFileBehind() throws IOException {
		Path target = folder().resolve("settings.json");
		store().writeAtomic(target, "{\"a\":1}");

		assertEquals("{\"a\":1}", Files.readString(target));
		assertFalse(Files.exists(target.resolveSibling("settings.json.tmp")));
	}

	@Test
	public void aQueuedWriteLandsOnFlush() throws IOException {
		ConfigStore store = store();
		Path target = store.fileFor(ConfigSection.STATE, null);
		store.queue(target, "{\"voteCount\":3}");
		store.flushNow();

		assertEquals("{\"voteCount\":3}", Files.readString(target));
	}

	@Test
	public void aSecondQueueForTheSameFileReplacesTheFirst() throws IOException {
		ConfigStore store = store();
		Path target = store.fileFor(ConfigSection.STATE, null);
		store.queue(target, "{\"voteCount\":1}");
		store.queue(target, "{\"voteCount\":2}");
		store.flushNow();

		assertEquals("{\"voteCount\":2}", Files.readString(target),
				"coalescing must keep the newest content, not the first");
	}

	// --- the format stamp is written but kept out of the merged view ---

	@Test
	public void theFormatStampIsStrippedOnRead() throws IOException {
		Files.createDirectories(folder());
		Files.writeString(folder().resolve("settings.json"),
				"{\"formatVersion\": 1, \"markerScale\": 2.0}");

		JsonObject merged = store().loadMerged(null);

		assertFalse(merged.has(ConfigStore.FORMAT_KEY));
		assertEquals(2.0, merged.get("markerScale").getAsDouble(), 0.0001);
	}

	// --- profile selection ---

	@Test
	public void theProfileNamedInSettingsIsTheOneLoaded() throws IOException {
		Files.createDirectories(folder().resolve("profiles"));
		Files.writeString(folder().resolve("settings.json"), "{\"activeProfile\": \"fishing\"}");
		Files.writeString(folder().resolve("profiles").resolve("fishing.json"),
				"{\"cooldownsStackVertical\": true}");
		Files.writeString(folder().resolve("profiles").resolve("default.json"),
				"{\"cooldownsStackVertical\": false}");

		JsonObject merged = store().loadMerged(null);

		assertTrue(merged.get("cooldownsStackVertical").getAsBoolean());
		assertEquals("fishing", merged.get("activeProfile").getAsString());
	}

	@Test
	public void anOverrideBeatsTheProfileNamedInSettings() throws IOException {
		Files.createDirectories(folder().resolve("profiles"));
		Files.writeString(folder().resolve("settings.json"), "{\"activeProfile\": \"fishing\"}");
		Files.writeString(folder().resolve("profiles").resolve("mining.json"),
				"{\"cooldownsStackVertical\": true}");

		JsonObject merged = store().loadMerged("mining");

		assertTrue(merged.get("cooldownsStackVertical").getAsBoolean());
		assertEquals("mining", merged.get("activeProfile").getAsString());
	}

	@Test
	public void listProfilesReportsWhatIsOnDisk() throws IOException {
		Files.createDirectories(folder().resolve("profiles"));
		Files.writeString(folder().resolve("profiles").resolve("default.json"), "{}");
		Files.writeString(folder().resolve("profiles").resolve("fishing.json"), "{}");
		Files.writeString(folder().resolve("profiles").resolve("notes.txt"), "ignored");

		assertEquals(java.util.List.of("default", "fishing"), store().listProfiles());
	}

	@Test
	public void listProfilesIsEmptyRatherThanNullWithNoFolder() {
		assertTrue(store().listProfiles().isEmpty());
	}

	@Test
	public void deletingAProfileRemovesItsFile() throws IOException {
		Files.createDirectories(folder().resolve("profiles"));
		Files.writeString(folder().resolve("profiles").resolve("fishing.json"), "{}");
		ConfigStore store = store();

		assertTrue(store.profileExists("fishing"));
		store.deleteProfile("fishing");
		assertFalse(store.profileExists("fishing"));
	}

	// --- migration from the pre-1.16 flat file ---

	@Test
	public void aFlatConfigIsUsedAsTheBaseLayerWhenTheFolderIsAbsent() throws IOException {
		Files.writeString(configDir.resolve("labsaddons.json"),
				"{\"markerScale\": 1.25, \"voteCount\": 7, \"cooldownsStackVertical\": true}");

		JsonObject merged = store().loadMerged(null);

		assertEquals(1.25, merged.get("markerScale").getAsDouble(), 0.0001);
		assertEquals(7, merged.get("voteCount").getAsInt());
		assertTrue(merged.get("cooldownsStackVertical").getAsBoolean());
	}

	@Test
	public void thePreRenameFishbiteFileIsStillPickedUp() throws IOException {
		Files.writeString(configDir.resolve("fishbite.json"), "{\"markerScale\": 3.0}");

		assertEquals(3.0, store().loadMerged(null).get("markerScale").getAsDouble(), 0.0001);
	}

	@Test
	public void labsaddonsJsonWinsOverTheOlderFishbiteJson() throws IOException {
		Files.writeString(configDir.resolve("labsaddons.json"), "{\"markerScale\": 1.0}");
		Files.writeString(configDir.resolve("fishbite.json"), "{\"markerScale\": 3.0}");

		assertEquals(1.0, store().loadMerged(null).get("markerScale").getAsDouble(), 0.0001);
	}

	@Test
	public void theFlatFileIsIgnoredOnceTheFolderExists() throws IOException {
		Files.writeString(configDir.resolve("labsaddons.json"), "{\"markerScale\": 3.0}");
		Files.createDirectories(folder());
		Files.writeString(folder().resolve("settings.json"), "{\"markerScale\": 1.0}");

		assertEquals(1.0, store().loadMerged(null).get("markerScale").getAsDouble(), 0.0001,
				"migration must not re-apply on every launch");
	}

	@Test
	public void migrationLeavesTheOldFileInPlaceAsABackup() throws IOException {
		Path legacy = configDir.resolve("labsaddons.json");
		Files.writeString(legacy, "{\"markerScale\": 1.25}");

		store().loadMerged(null);

		assertTrue(Files.exists(legacy));
	}

	@Test
	public void aSectionFilePresentMeansNothingIsReportedMissing() throws IOException {
		Files.createDirectories(folder().resolve("profiles"));
		for (String name : new String[] {"settings.json", "state.json", "runners.json"}) {
			Files.writeString(folder().resolve(name), "{}");
		}
		Files.writeString(folder().resolve("profiles").resolve("default.json"), "{}");

		ConfigStore store = store();
		store.loadMerged(null);
		assertFalse(store.sectionsWereMissing());
	}

	@Test
	public void aDeletedSectionIsReportedSoTheCallerRewritesIt() throws IOException {
		Files.createDirectories(folder());
		Files.writeString(folder().resolve("settings.json"), "{}");

		ConfigStore store = store();
		store.loadMerged(null);
		assertTrue(store.sectionsWereMissing());
	}

	@Test
	public void noFileForLegacyOrProfileSectionsByName() {
		assertNull(ConfigSection.LEGACY.fileName());
		assertNull(ConfigSection.PROFILE.fileName());
		assertEquals("settings.json", ConfigSection.SETTINGS.fileName());
	}
}
