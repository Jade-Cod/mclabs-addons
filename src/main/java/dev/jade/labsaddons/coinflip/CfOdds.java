package dev.jade.labsaddons.coinflip;

import java.util.Locale;

/**
 * What a coinflip costs and what it pays.
 *
 * <p>Two numbers settle the whole game, and both were measured rather than assumed. The
 * lobby's book states "Tax: 5%", and a $600,000 flip returned $1,140,000 — which is 5% of
 * the $1,200,000 pot, not 5% of the profit. So a win returns 1.90x the stake, a loss
 * returns nothing, and the house edge is a flat -5% at every wager. That makes this the
 * cheapest table on the server: Double² is -6.9% and Mines is -30%.
 *
 * <p>Whole cents throughout, because the figures the server prints are not whole dollars:
 * "Total Wagered: 1,231,357,591.65".
 */
public final class CfOdds {
	/** The house's cut, as a percentage of the pot. */
	private static final long TAX_PERCENT = 5L;
	private static final long PERCENT = 100L;
	private static final long CENTS = 100L;
	/**
	 * Whole dollars wagered per quarter of an Investor Rewards Point. Two samples, both
	 * exact: $750 earned 1.75 points and $500,000 earned 1,250 — a flat 0.25% of the
	 * wager, rounded down to the nearest quarter point.
	 */
	private static final long DOLLARS_PER_QUARTER_POINT = 100L;
	private static final int QUARTERS = 4;

	private CfOdds() {
	}

	/** Both stakes together: what the winner is playing for before tax. */
	public static long potCents(long wagerCents) {
		return wagerCents * 2L;
	}

	public static long taxCents(long wagerCents) {
		return potCents(wagerCents) * TAX_PERCENT / PERCENT;
	}

	/** What the winner collects: the pot less the tax, 1.90x the stake. */
	public static long winReturnCents(long wagerCents) {
		return potCents(wagerCents) - taxCents(wagerCents);
	}

	/** What the winner is up on the flip, 0.90x the stake. */
	public static long winProfitCents(long wagerCents) {
		return winReturnCents(wagerCents) - wagerCents;
	}

	/**
	 * What the game costs on average, whoever wins: half the tax, so 5% of the stake.
	 * Also what a lifetime of wagering was always going to cost, applied to the total.
	 */
	public static long expectedLossCents(long wageredCents) {
		return wageredCents * TAX_PERCENT / PERCENT;
	}

	/** Quarter points earned: 7 on a $750 flip (1.75), 5,000 on $500,000 (1,250). */
	public static long investorQuarterPoints(long wagerCents) {
		return wagerCents / CENTS / DOLLARS_PER_QUARTER_POINT;
	}

	/** "1.75", "1,250" — the points as the server would print them. */
	public static String investorPointsText(long wagerCents) {
		long quarters = investorQuarterPoints(wagerCents);
		String whole = String.format(Locale.ROOT, "%,d", quarters / QUARTERS);
		return whole + switch ((int) (quarters % QUARTERS)) {
			case 1 -> ".25";
			case 2 -> ".5";
			case 3 -> ".75";
			default -> "";
		};
	}

	/**
	 * How far a record sits from an even coin, in standard deviations. A losing streak
	 * only means something once it is bigger than the tax that explains it.
	 */
	public static double luckSigma(int played, int won) {
		if (played <= 0) {
			return 0d;
		}
		return (2d * won - played) / Math.sqrt(played);
	}

	/** "-1.4σ", or "—" with nothing to measure. */
	public static String sigmaText(int played, int won) {
		if (played <= 0) {
			return "—";
		}
		return String.format(Locale.ROOT, "%+.1f", luckSigma(played, won)) + "σ";
	}
}
