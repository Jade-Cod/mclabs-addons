package dev.jade.fishbite.labwars;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses Lab Wars durations: "1 hour", "30 minutes", "10m:50s", "02h:10m". */
public final class LabWarsDuration {
	private static final Pattern PART =
			Pattern.compile("(\\d+)\\s*(day|hour|minute|second|d|h|m|s)", Pattern.CASE_INSENSITIVE);

	private LabWarsDuration() {
	}

	public static long parseMs(String text) {
		long totalMs = 0;
		Matcher part = PART.matcher(text.toLowerCase(Locale.ROOT));
		while (part.find()) {
			long amount = Long.parseLong(part.group(1));
			totalMs += switch (Character.toLowerCase(part.group(2).charAt(0))) {
				case 'd' -> amount * 86_400_000L;
				case 'h' -> amount * 3_600_000L;
				case 'm' -> amount * 60_000L;
				default -> amount * 1_000L;
			};
		}
		return totalMs;
	}
}
