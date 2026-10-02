package dev.jade.labsaddons.coinflip;

import java.util.ArrayList;
import java.util.List;

/**
 * The /cf create form's wager limits and quick-add chips. Minecraft-free, so the rules are
 * tested without a game.
 *
 * <p>A chip adds its value to the wager rather than replacing it, so a few clicks build any
 * amount; the total stops at {@link #MAX}. Players set their own four values from the form's
 * cog, kept in the config.
 */
public final class CfChips {
	/** The server's limits for a typed wager; "balance" is its own case and not held to these. */
	public static final long MIN = 200;
	public static final long MAX = 100_000_000;
	public static final int COUNT = 4;
	public static final List<Long> DEFAULTS = List.of(500_000L, 1_000_000L, 5_000_000L, 10_000_000L);

	private CfChips() {
	}

	public static boolean valid(long wager) {
		return wager >= MIN && wager <= MAX;
	}

	/** The wager after one click of {@code chip}, capped at {@link #MAX}. */
	public static long add(long wager, long chip) {
		return Math.min(MAX, Math.max(0, wager) + chip);
	}

	/** "500k", "1m", "2.5m", "750": short enough for a chip a quarter of the rail wide. */
	public static String label(long value) {
		if (value >= 1_000_000 && value % 100_000 == 0) {
			return trim(value / 1_000_000.0) + "m";
		}
		if (value >= 1_000 && value % 100 == 0) {
			return trim(value / 1_000.0) + "k";
		}
		return String.valueOf(value);
	}

	private static String trim(double value) {
		return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
	}

	/** A saved set of chips if it is four valid values, else the defaults. */
	public static List<Long> sanitize(List<Long> saved) {
		if (saved == null || saved.size() != COUNT) {
			return new ArrayList<>(DEFAULTS);
		}
		for (Long value : saved) {
			if (value == null || !valid(value)) {
				return new ArrayList<>(DEFAULTS);
			}
		}
		return new ArrayList<>(saved);
	}
}
