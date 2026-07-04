package dev.jade.fishbite.update;

/**
 * Minimal dotted-numeric version comparator (e.g. "1.12.1" vs "1.12.10"), comparing
 * each dot-separated segment numerically instead of lexicographically. Not full
 * semver (no pre-release precedence) - this mod has only ever used plain x.y.z.
 */
final class ModVersion {
	private ModVersion() {
	}

	/** True if {@code remote} is strictly newer than {@code current}. */
	static boolean isNewer(String remote, String current) {
		int[] r = parse(remote);
		int[] c = parse(current);
		int length = Math.max(r.length, c.length);
		for (int i = 0; i < length; i++) {
			int rp = i < r.length ? r[i] : 0;
			int cp = i < c.length ? c[i] : 0;
			if (rp != cp) {
				return rp > cp;
			}
		}
		return false;
	}

	private static int[] parse(String version) {
		String[] parts = version.trim().split("[.\\-+]");
		int[] out = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			out[i] = leadingInt(parts[i]);
		}
		return out;
	}

	/** Leading digit run of a segment (e.g. 1 from "1-beta"), else 0. */
	private static int leadingInt(String segment) {
		int end = 0;
		while (end < segment.length() && Character.isDigit(segment.charAt(end))) {
			end++;
		}
		if (end == 0) {
			return 0;
		}
		try {
			return Integer.parseInt(segment.substring(0, end));
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
