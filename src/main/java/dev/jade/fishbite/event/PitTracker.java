package dev.jade.fishbite.event;

import dev.jade.fishbite.config.FishBiteConfig;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks "The Pit" open window from its chat announcements, which the server
 * re-broadcasts periodically (e.g. "The pit is currently open for another
 * 30m:00s!"). Each message refreshes the remaining time.
 */
public final class PitTracker {
	// "open for another <time>" is authoritative (covers status + extend messages);
	// a bare "sponsored ... for <time>" only raises the floor.
	private static final Pattern OPEN = Pattern.compile(
			"open for another\\s+(.+?)\\s*[!.]", Pattern.CASE_INSENSITIVE);
	private static final Pattern SPONSOR = Pattern.compile(
			"sponsored The Pit for\\s+(.+?)\\s*[!.]", Pattern.CASE_INSENSITIVE);

	private PitTracker() {
	}

	public static void onMessage(String text) {
		FishBiteConfig config = FishBiteConfig.get();
		Matcher open = OPEN.matcher(text);
		if (open.find()) {
			long ms = dev.jade.fishbite.hud.Durations.parseMs(open.group(1));
			if (ms > 0) {
				config.pitExpiryEpochMs = System.currentTimeMillis() + ms;
				config.save();
			}
			return;
		}
		Matcher sponsor = SPONSOR.matcher(text);
		if (sponsor.find()) {
			long ms = dev.jade.fishbite.hud.Durations.parseMs(sponsor.group(1));
			if (ms > 0) {
				config.pitExpiryEpochMs = Math.max(config.pitExpiryEpochMs, System.currentTimeMillis() + ms);
				config.save();
			}
		}
	}

	public static boolean isActive() {
		return FishBiteConfig.get().pitExpiryEpochMs > System.currentTimeMillis();
	}

	public static long remainingMs() {
		return Math.max(0L, FishBiteConfig.get().pitExpiryEpochMs - System.currentTimeMillis());
	}

	public static void clear() {
		FishBiteConfig config = FishBiteConfig.get();
		config.pitExpiryEpochMs = 0L;
		config.save();
	}
}
