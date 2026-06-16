package dev.jade.fishbite.chum;

import dev.jade.fishbite.config.FishBiteConfig;
import dev.jade.fishbite.hud.Durations;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks the Chum Bucket double-fish buff. Each activation adds 20 minutes,
 * stacking on top of any remaining time. The expiry is stored as an absolute
 * epoch millis in the config so it survives relogs (the server buff is real
 * time-based, so the countdown keeps pace while you're away).
 */
public final class ChumTimer {
	public static final long CHUM_DURATION_MS = 20L * 60L * 1000L;

	private static final Pattern DOUBLE_FISH_PURCHASE = Pattern.compile(
			"purchased\\s+(.+?)\\s+of double fish", Pattern.CASE_INSENSITIVE);

	private ChumTimer() {
	}

	/** Chat: "<player> has just purchased N minutes of double fish for the whole lab!" */
	public static void onMessage(String text) {
		Matcher m = DOUBLE_FISH_PURCHASE.matcher(text);
		if (m.find()) {
			long ms = Durations.parseMs(m.group(1));
			if (ms > 0) {
				addDuration(ms);
			}
		}
	}

	/** Adds one Chum Bucket (20 min), stacking on remaining time. */
	public static void addChum() {
		addDuration(CHUM_DURATION_MS);
	}

	/** Adds an arbitrary amount of chum time, stacking on remaining time. */
	public static void addDuration(long durationMs) {
		FishBiteConfig config = FishBiteConfig.get();
		long base = Math.max(System.currentTimeMillis(), config.chumExpiryEpochMs);
		config.chumExpiryEpochMs = base + durationMs;
		config.save();
	}

	public static void reset() {
		FishBiteConfig config = FishBiteConfig.get();
		config.chumExpiryEpochMs = 0L;
		config.save();
	}

	public static long remainingMs() {
		return Math.max(0L, FishBiteConfig.get().chumExpiryEpochMs - System.currentTimeMillis());
	}

	public static boolean isActive() {
		return remainingMs() > 0L;
	}

	/** Remaining time as {@code M:SS} (or {@code MM:SS}). */
	public static String formatRemaining() {
		long totalSeconds = (remainingMs() + 999L) / 1000L;
		long minutes = totalSeconds / 60L;
		long seconds = totalSeconds % 60L;
		return String.format("%d:%02d", minutes, seconds);
	}
}
