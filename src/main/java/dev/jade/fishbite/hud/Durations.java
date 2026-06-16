package dev.jade.fishbite.hud;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses human durations: "1w 1d 8h 44m 30s", "30 minutes", "30m:00s", "59 minutes". */
public final class Durations {
	private static final Pattern PART = Pattern.compile(
			"(\\d+)\\s*(week|day|hour|minute|second|w|d|h|m|s)", Pattern.CASE_INSENSITIVE);

	private Durations() {
	}

	public static long parseMs(String text) {
		if (text == null) {
			return 0;
		}
		long total = 0;
		Matcher m = PART.matcher(text.toLowerCase(Locale.ROOT));
		while (m.find()) {
			long amount = Long.parseLong(m.group(1));
			total += switch (m.group(2).charAt(0)) {
				case 'w' -> amount * 604_800_000L;
				case 'd' -> amount * 86_400_000L;
				case 'h' -> amount * 3_600_000L;
				case 'm' -> amount * 60_000L;
				default -> amount * 1_000L;
			};
		}
		return total;
	}
}
