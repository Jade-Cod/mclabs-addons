package dev.jade.labsaddons.update;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * Builds the Modrinth links behind the update notice — no Minecraft or
 * FabricLoader deps, so it's unit-testable.
 */
final class ModrinthLink {
	static final String PROJECT_URL = "https://modrinth.com/mod/mclabs-addons";

	// The id comes from the API, so it is checked before going into a URL path
	// rather than trusted. Modrinth's own ids are short alphanumeric strings.
	private static final Pattern URL_SAFE_ID = Pattern.compile("[A-Za-z0-9._+-]{1,64}");

	private ModrinthLink() {
	}

	/**
	 * The given release's own Modrinth page, addressed by Modrinth's version id
	 * (e.g. "sjcFGe03") rather than its version number.
	 *
	 * <p>The mod publishes the same version number once per Minecraft line, and a
	 * by-number URL resolves to only one of those entries — Modrinth's choice, not
	 * ours. The id is the only way to be certain a player is sent the jar built for
	 * the game version they are actually running.
	 *
	 * <p>Falls back to the project page when the id can't be put in a URL path.
	 */
	static URI downloadUri(String versionId) {
		if (versionId != null && URL_SAFE_ID.matcher(versionId).matches()) {
			try {
				return URI.create(PROJECT_URL + "/version/" + versionId);
			} catch (IllegalArgumentException e) {
				// fall through to the project page
			}
		}
		return URI.create(PROJECT_URL);
	}
}
