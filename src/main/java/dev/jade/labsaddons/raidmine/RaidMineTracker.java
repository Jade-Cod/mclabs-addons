package dev.jade.labsaddons.raidmine;

import dev.jade.labsaddons.hud.Durations;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks the Raid Mine's double-drops buff from its chat announcement
 * ("Double mine drops for 15 seconds!"). The line is private to the player who
 * procced it, so every match is ours — no sender check is needed.
 *
 * <p>Each proc stacks on whatever time is left, the same way the Chum Bucket
 * does. The duration is read out of the message rather than hard-coded, so the
 * 15s and 30s rolls (and any other the server adds) all work.
 *
 * <p>ponytail: in-memory, not persisted like the other timers. The buff is
 * seconds long — it cannot survive a relog, so there is nothing worth writing
 * to disk, and a proc every 15s would mean a config write every 15s.
 */
public final class RaidMineTracker {
	// Bounded at punctuation so a trailing sentence can't contribute a second
	// duration; Durations picks the "15 seconds" out of whatever is captured.
	private static final Pattern DOUBLE_DROPS = Pattern.compile(
			"double mine drops for\\s+([^!.\\n\\r]+)", Pattern.CASE_INSENSITIVE);

	private static long expiryEpochMs;

	private RaidMineTracker() {
	}

	public static void onMessage(String text) {
		onMessage(text, System.currentTimeMillis());
	}

	static void onMessage(String text, long nowMs) {
		Matcher matcher = DOUBLE_DROPS.matcher(text);
		if (!matcher.find()) {
			return;
		}
		long durationMs = Durations.parseMs(matcher.group(1));
		if (durationMs > 0) {
			// max(now, expiry): stack onto remaining time, but never onto a stale
			// expiry already in the past.
			expiryEpochMs = Math.max(nowMs, expiryEpochMs) + durationMs;
		}
	}

	public static boolean isActive() {
		return remainingMs() > 0L;
	}

	public static long remainingMs() {
		return remainingMs(System.currentTimeMillis());
	}

	static long remainingMs(long nowMs) {
		return Math.max(0L, expiryEpochMs - nowMs);
	}

	public static void clear() {
		expiryEpochMs = 0L;
	}
}
