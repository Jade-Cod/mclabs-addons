package dev.jade.labsaddons.config;

/**
 * Which file a {@link LabsAddonsConfig} field is persisted to, chosen by how the
 * data behaves rather than which feature owns it. Splitting by lifecycle is what
 * keeps the file count at three: a new feature adds fields to existing sections
 * instead of adding a file.
 */
public enum ConfigSection {
	/** Preferences the player chose. Small, durable, written when they change something. */
	SETTINGS("settings.json"),
	/** Server-derived and volatile — timers, boosters, scraped boards. Rewritten from chat. */
	STATE("state.json"),
	/** The runner leaderboard. The only section that grows without bound. */
	RUNNERS("runners.json"),
	/** Per-profile HUD layout and widget display prefs; lives under {@code profiles/}. */
	PROFILE(null),
	/**
	 * Fields kept only so an old config still deserializes into
	 * {@link LabsAddonsConfig#sanitized()}. Written to no file, so they vanish
	 * from disk the first time the new layout is saved.
	 */
	LEGACY(null);

	private final String fileName;

	ConfigSection(String fileName) {
		this.fileName = fileName;
	}

	/** File name within {@code config/labsaddons/}, or null for sections with no fixed file. */
	public String fileName() {
		return fileName;
	}
}
