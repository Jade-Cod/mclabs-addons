package dev.jade.labsaddons.mines;

import java.util.Locale;

/**
 * The arithmetic behind the Mines board.
 *
 * <p>The server pays <b>exactly 70% of the fair multiplier</b> at every rung. Measured
 * against all four figures it printed during the captured round — 25 tiles, 9 mines,
 * $3,500 staked:
 *
 * <pre>
 *   stars   fair     x0.70    server said
 *     1     1.5625   1.094    $3,828.25
 *     2     2.5000   1.750    $6,125
 *     3     4.1071   2.875    $10,062.50
 *     4     6.9505   4.865    $17,028.46   (39c low — the only one that misses)
 * </pre>
 *
 * <p>Because the cut is flat at every rung, cashing out and revealing one more tile have
 * <b>identical expected value</b>: 70c on the dollar either way. Revealing more only
 * widens the spread. The board states the figures and lets that speak for itself.
 *
 * <p>Only one mine count has been sampled, so the rate is an observation rather than a
 * rule. Every figure the server states on the Cash Out item is preferred over anything
 * computed here; these numbers exist for the rungs you have not reached yet, and for the
 * betting screen, where the server states nothing at all.
 *
 * <p>Everything is kept as a ratio of whole numbers until the last possible moment.
 * A running product of doubles was the first attempt and it made rung three read 2.87x
 * where the true figure is exactly 2.875 — small, but wrong on screen.
 */
public final class MinesOdds {
	public static final int TILES = 25;
	/** The share of a fair payout the server actually returns, as a ratio. */
	private static final int RETURN_NUMERATOR = 7;
	private static final int RETURN_DENOMINATOR = 10;
	/** The measured rate, for anything that wants to state it. */
	public static final double RETURN_RATE =
			(double) RETURN_NUMERATOR / RETURN_DENOMINATOR;

	private MinesOdds() {
	}

	/**
	 * The fair multiplier after {@code stars} safe tiles: the reciprocal of the chance of
	 * getting that far, {@code C(25,k) / C(25-mines,k)}.
	 */
	public static double fair(int mines, int stars) {
		if (!valid(mines, stars)) {
			return 0.0;
		}
		return (double) ways(TILES, stars) / ways(safeTiles(mines), stars);
	}

	/** What the server pays for {@code stars} safe tiles. */
	public static double multiplier(int mines, int stars) {
		if (!valid(mines, stars)) {
			return 0.0;
		}
		// One division of two exact integers, so a figure like 2.875 lands on itself.
		return (double) (ways(TILES, stars) * RETURN_NUMERATOR)
				/ (ways(safeTiles(mines), stars) * RETURN_DENOMINATOR);
	}

	/**
	 * That multiplier applied to a stake, in cents, rounded half up.
	 *
	 * <p>Done in whole numbers so no cent is lost to binary fractions. A stake large
	 * enough to overflow that falls back to the double path, which at that size is no
	 * longer being counted in cents by anyone.
	 */
	public static long payoutCents(long stakeCents, int mines, int stars) {
		if (!valid(mines, stars) || stakeCents <= 0) {
			return 0L;
		}
		long numerator = ways(TILES, stars) * RETURN_NUMERATOR;
		long denominator = ways(safeTiles(mines), stars) * RETURN_DENOMINATOR;
		try {
			long scaled = Math.addExact(Math.multiplyExact(stakeCents, numerator),
					denominator / 2);
			return scaled / denominator;
		} catch (ArithmeticException overflow) {
			return Math.round(stakeCents * multiplier(mines, stars));
		}
	}

	/**
	 * The chance the next tile you click is a mine, given {@code stars} already revealed
	 * safe. Exact: every unrevealed tile is equally likely, and none of the mines have
	 * been found yet — if one had, the round would be over.
	 */
	public static double mineChance(int mines, int stars) {
		int remaining = TILES - stars;
		if (mines <= 0 || remaining <= 0) {
			return 0.0;
		}
		return Math.min(1.0, (double) mines / remaining);
	}

	/** How many safe tiles there are to find in total. */
	public static int safeTiles(int mines) {
		return Math.max(0, TILES - mines);
	}

	/** "2.88x", the label the ladder wants. */
	public static String multiplierText(int mines, int stars) {
		return String.format(Locale.ROOT, "%.2fx", multiplier(mines, stars));
	}

	private static boolean valid(int mines, int stars) {
		return mines >= 0 && mines < TILES && stars >= 0 && stars <= safeTiles(mines);
	}

	/**
	 * {@code C(n, k)}, built up a factor at a time. Each step divides exactly, so this
	 * stays in whole numbers throughout — and the largest value it ever reaches here is
	 * {@code C(25,12)}, about five million.
	 */
	private static long ways(int n, int k) {
		long result = 1L;
		for (int i = 0; i < k; i++) {
			result = result * (n - i) / (i + 1);
		}
		return result;
	}
}
