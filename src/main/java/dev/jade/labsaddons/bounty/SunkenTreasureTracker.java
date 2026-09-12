package dev.jade.labsaddons.bounty;

import java.util.Locale;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks the Fishing Weekend "Sunken Treasure" event from its {@code Fishing Weekend »}
 * chat lines: how many barrels are still hidden. Shape is deliberately the same as
 * {@link BountyTracker} — the event works the same way, a batch of hidden things with a
 * broadcast count as players find them — so state is in-memory only and the "N left"
 * lines are authoritative, meaning a missed message self-heals on the next one.
 *
 * <p>Each wave re-announces "Find one of the N sunken barrels", which re-seeds the count.
 * There is no observed end-of-event line, so the row simply goes away when the count
 * reaches zero.
 */
public final class SunkenTreasureTracker {
	/** "Sunken Treasure! Find one of the 6 sunken barrels along the shorelines of Spawn ...". */
	private static final Pattern START = Pattern.compile(
			"Find one of the\\s+(\\d+)\\s+sunken barrels", Pattern.CASE_INSENSITIVE);
	/** "NAME has found a sunken treasure! 4 left in the Spawn waters." */
	private static final Pattern LEFT = Pattern.compile(
			"has found a sunken treasure!\\s+(one|\\d+)\\s+left", Pattern.CASE_INSENSITIVE);

	/**
	 * "Yeomans506 has found the final treasure! All sunken treasures have been found!"
	 * The last claim of a wave says this instead of "0 left", so without it the row sat
	 * at "1 left" forever.
	 */
	private static final Pattern FINAL = Pattern.compile(
			"all sunken treasures have been found", Pattern.CASE_INSENSITIVE);

	/**
	 * A wave nobody finished (the weekend ended with barrels still out, or we missed the
	 * final line) would otherwise stay up until restart. Waves re-seed every 20-50 minutes
	 * while the event runs, so an hour without any treasure news means it's over.
	 */
	// ponytail: fixed timeout, raise it if MCLabs ever spaces waves further apart.
	static final long STALE_MS = 60 * 60 * 1000L;

	private static volatile boolean active;
	private static volatile int remaining;
	private static volatile long lastSeenMs;

	// Swappable so tests can age a wave without sleeping.
	static LongSupplier clock = System::currentTimeMillis;

	private SunkenTreasureTracker() {
	}

	public static synchronized void onMessage(String text) {
		if (FINAL.matcher(text).find()) {
			clear();
			return;
		}
		Matcher start = START.matcher(text);
		if (start.find()) {
			remaining = parseInt(start.group(1));
			active = remaining > 0;
			lastSeenMs = clock.getAsLong();
			return;
		}
		// "Sunken treasure found for 50 score!" (your own find) carries no count, and the
		// broadcast line with the new count lands right behind it — nothing to do here.
		Matcher left = LEFT.matcher(text);
		if (left.find()) {
			remaining = parseCount(left.group(1));
			active = remaining > 0;
			lastSeenMs = clock.getAsLong();
		}
	}

	private static int parseCount(String value) {
		return value.equalsIgnoreCase("one") ? 1 : parseInt(value);
	}

	private static int parseInt(String value) {
		try {
			return Integer.parseInt(value.toLowerCase(Locale.ROOT));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** Authoritative count from the {@code /fw} GUI, which knows even if we missed the chat. */
	public static synchronized void reconcile(int crates) {
		remaining = Math.max(0, crates);
		active = remaining > 0;
		lastSeenMs = clock.getAsLong();
	}

	public static synchronized boolean isActive() {
		return active && remaining > 0 && clock.getAsLong() - lastSeenMs < STALE_MS;
	}

	public static synchronized int remaining() {
		return remaining;
	}

	public static synchronized void clear() {
		active = false;
		remaining = 0;
	}
}
