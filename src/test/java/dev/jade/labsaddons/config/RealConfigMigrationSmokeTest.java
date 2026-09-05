package dev.jade.labsaddons.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Migrates a real, full-sized config from a live install and reports what came out.
 * Skips silently unless {@code REAL_CONFIG} points at one, so it is a no-op in CI:
 *
 * <pre>REAL_CONFIG=~/.minecraft/config/labsaddons.json ./gradlew test --tests '*RealConfigMigrationSmokeTest*' -i</pre>
 *
 * <p>Worth keeping. It is what caught the section exclusion strategy reaching into
 * nested types, which wrote every widget and runner as {@code {}} — a synthetic
 * fixture had too few moving parts to show it, but the byte counts here made it
 * obvious at a glance.
 */
public class RealConfigMigrationSmokeTest {
	@Test
	public void migrateRealConfig() throws Exception {
		String supplied = System.getenv("REAL_CONFIG");
		if (supplied == null || supplied.isBlank() || !Files.isRegularFile(Path.of(supplied))) {
			System.out.println("SKIP: no real config supplied");
			return;
		}
		Path src = Path.of(supplied);
		Path dir = Files.createTempDirectory("labsaddons-smoke");
		Files.copy(src, dir.resolve("labsaddons.json"));
		long before = Files.size(src);

		LabsAddonsConfig.useStore(new ConfigStore(dir));
		LabsAddonsConfig config = LabsAddonsConfig.get();

		System.out.println("=== migrated from " + before + " bytes ===");
		try (var walk = Files.walk(dir.resolve("labsaddons"))) {
			walk.filter(Files::isRegularFile).sorted().forEach(p -> {
				try {
					System.out.printf("  %-34s %6d bytes%n",
							dir.resolve("labsaddons").relativize(p), Files.size(p));
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
		}
		System.out.println("widgets carried over : " + config.hudObjects.size());
		System.out.println("runners carried over : " + config.runnerStats.size());
		System.out.println("prestige tracks      : " + config.prestigeChems.size());
		System.out.println("boosters             : " + config.boosters.size());
		System.out.println("chum expiry          : " + config.chumExpiryEpochMs);
		System.out.println("marker scale         : " + config.markerScale);
		System.out.println("active profile       : " + config.activeProfile);
		System.out.println("old file still there : " + Files.exists(dir.resolve("labsaddons.json")));
		LabsAddonsConfig.useStore(null);
	}
}
