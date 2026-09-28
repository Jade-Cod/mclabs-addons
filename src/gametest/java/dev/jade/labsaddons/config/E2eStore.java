package dev.jade.labsaddons.config;

import java.nio.file.Path;

/**
 * The harness's one reach into the config package, the same seam the unit tests use:
 * point the mod at an empty config folder, as a fresh install would see it.
 */
public final class E2eStore {
	private E2eStore() {
	}

	/** Call on the client thread; the next {@link LabsAddonsConfig#get()} loads from here. */
	public static void fresh(Path configDir) {
		LabsAddonsConfig.get().saveNow();
		LabsAddonsConfig.useStore(new ConfigStore(configDir));
	}
}
