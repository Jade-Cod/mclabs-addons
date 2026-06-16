package dev.jade.fishbite.event;

import dev.jade.fishbite.chum.ChumTimer;
import dev.jade.fishbite.config.FishBiteConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks the server's mini-events from chat: an upcoming countdown
 * ("A Mini-Event will begin in 10 minutes!"), then the active event with its
 * type, end time, and first-place reward. Fishing events also grant 30 minutes
 * of chum time, matching the server's behaviour.
 */
public final class MiniEventTracker {
	private static final Logger LOGGER = LoggerFactory.getLogger("fishbite");

	private static final Pattern UPCOMING =
			Pattern.compile("Mini-?Event will begin in\\s+(.+?)\\s*[!.\\n\\r]", Pattern.CASE_INSENSITIVE);
	private static final Pattern BEGUN =
			Pattern.compile("([A-Za-z][A-Za-z' ]*?)\\s+event has begun!", Pattern.CASE_INSENSITIVE);
	private static final Pattern ENDS =
			Pattern.compile("Mini-event ends in\\s+(.+?)[.\\n\\r]", Pattern.CASE_INSENSITIVE);
	private static final Pattern DURATION =
			Pattern.compile("(\\d+)\\s*(day|hour|minute|second|d|h|m|s)", Pattern.CASE_INSENSITIVE);

	private static final long FISHING_CHUM_MS = 30L * 60L * 1000L;
	private static final long DEFAULT_EVENT_MS = 30L * 60L * 1000L;
	private static long lastFishingGrantMs;

	private MiniEventTracker() {
	}

	public static void onMessage(String text) {
		Matcher upcoming = UPCOMING.matcher(text);
		if (upcoming.find()) {
			long ms = parseDuration(upcoming.group(1));
			if (ms > 0) {
				FishBiteConfig config = FishBiteConfig.get();
				config.miniEventUpcomingEpochMs = System.currentTimeMillis() + ms;
				config.save();
			}
		}

		Matcher begun = BEGUN.matcher(text);
		if (begun.find()) {
			startEvent(begun.group(1).trim());
		}

		FishBiteConfig config = FishBiteConfig.get();
		if (!config.miniEventType.isEmpty()) {
			Matcher ends = ENDS.matcher(text);
			if (ends.find()) {
				long ms = parseDuration(ends.group(1));
				if (ms > 0) {
					config.miniEventExpiryEpochMs = System.currentTimeMillis() + ms;
					config.save();
				}
			}
		}
	}

	private static void startEvent(String type) {
		FishBiteConfig config = FishBiteConfig.get();
		config.miniEventType = type;
		config.miniEventExpiryEpochMs = System.currentTimeMillis() + DEFAULT_EVENT_MS;
		config.miniEventUpcomingEpochMs = 0L;
		config.save();
		LOGGER.info("[fishbite] Mini-event started: {}", type);

		if (type.toLowerCase(Locale.ROOT).contains("fishing")) {
			long now = System.currentTimeMillis();
			if (now - lastFishingGrantMs > 30_000L) {
				lastFishingGrantMs = now;
				ChumTimer.addDuration(FISHING_CHUM_MS);
			}
		}
	}

	private static long parseDuration(String text) {
		long totalMs = 0;
		Matcher part = DURATION.matcher(text.toLowerCase(Locale.ROOT));
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

	public static boolean isActive() {
		return !FishBiteConfig.get().miniEventType.isEmpty()
				&& FishBiteConfig.get().miniEventExpiryEpochMs > System.currentTimeMillis();
	}

	public static boolean isUpcoming() {
		return !isActive()
				&& FishBiteConfig.get().miniEventUpcomingEpochMs > System.currentTimeMillis();
	}

	public static long activeRemainingMs() {
		return Math.max(0L, FishBiteConfig.get().miniEventExpiryEpochMs - System.currentTimeMillis());
	}

	public static long upcomingRemainingMs() {
		return Math.max(0L, FishBiteConfig.get().miniEventUpcomingEpochMs - System.currentTimeMillis());
	}

	public static String type() {
		return FishBiteConfig.get().miniEventType;
	}

	public static void clear() {
		FishBiteConfig config = FishBiteConfig.get();
		config.miniEventType = "";
		config.miniEventExpiryEpochMs = 0L;
		config.miniEventUpcomingEpochMs = 0L;
		config.save();
	}
}
