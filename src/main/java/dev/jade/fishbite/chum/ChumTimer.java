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
	/** /chum reply: "Double fish time remaining: 19m 53s" — authoritative remaining. */
	private static final Pattern TIME_REMAINING = Pattern.compile(
			"Double fish time remaining:\\s*(.+)", Pattern.CASE_INSENSITIVE);
	/** /chum reply: "You don't currently have double fish!" — buff is gone. */
	private static final Pattern NO_DOUBLE_FISH = Pattern.compile(
			"don't currently have double fish", Pattern.CASE_INSENSITIVE);

	private ChumTimer() {
	}

	/**
	 * Parses chum chat. {@code /chum} replies sync the timer to the server's
	 * authoritative value (replacing any local stack); a "purchased N of double
	 * fish" announcement stacks on top of the remaining time.
	 */
	public static void onMessage(String text) {
		Matcher remaining = TIME_REMAINING.matcher(text);
		if (remaining.find()) {
			setRemaining(Durations.parseMs(remaining.group(1)));
			return;
		}
		if (NO_DOUBLE_FISH.matcher(text).find()) {
			setRemaining(0L);
			return;
		}
		Matcher purchase = DOUBLE_FISH_PURCHASE.matcher(text);
		if (purchase.find()) {
			long ms = Durations.parseMs(purchase.group(1));
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

	/**
	 * Replaces the timer with an absolute remaining duration (authoritative /chum
	 * sync). {@code durationMs <= 0} clears the timer.
	 */
	public static void setRemaining(long durationMs) {
		FishBiteConfig config = FishBiteConfig.get();
		config.chumExpiryEpochMs = durationMs <= 0L ? 0L : System.currentTimeMillis() + durationMs;
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
