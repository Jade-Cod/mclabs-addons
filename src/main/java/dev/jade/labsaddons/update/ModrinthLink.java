package dev.jade.labsaddons.update;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * Builds the Modrinth links behind the update notice — no Minecraft or
 * FabricLoader deps, so it's unit-testable.
 */
final class ModrinthLink {
	static final String PROJECT_URL = "https://modrinth.com/mod/mclabs-addons";

	// version_number comes from the API, so it is checked before going into a URL
	// path rather than trusted. Modrinth's own versions are plain x.y.z.
	private static final Pattern URL_SAFE_VERSION = Pattern.compile("[A-Za-z0-9._+-]{1,64}");

	private ModrinthLink() {
	}

	/**
	 * The given release's own Modrinth page, falling back to the project page when
	 * the reported version can't be put in a URL path.
	 */
	static URI downloadUri(String version) {
		if (version != null && URL_SAFE_VERSION.matcher(version).matches()) {
			try {
				return URI.create(PROJECT_URL + "/version/" + version);
			} catch (IllegalArgumentException e) {
				// fall through to the project page
			}
		}
		return URI.create(PROJECT_URL);
	}
}
