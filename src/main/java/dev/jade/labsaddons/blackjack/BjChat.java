package dev.jade.labsaddons.blackjack;

import dev.jade.labsaddons.casino.Money;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the last BondJoules hand paid, read from the server's own words.
 *
 * <p>Needed because the status band states a figure only when you win — "Perfect
 * Reaction! You win $7,750!" — and says nothing about the money on a loss or a push. Chat
 * states all three.
 *
 * <p>A dealt 21 returns 2.5x the stake, any other win 2x, and a push the stake itself.
 * All three are measured, so the betting board can state what each outcome is worth —
 * but what actually happened still comes from here, not from a calculation.
 */
public final class BjChat {
	/** What the last finished hand did to your money. */
	public record Outcome(long amountCents, boolean won, boolean push) {
	}

	private static final Pattern WON = Pattern.compile(
			"beat the competing lab and earned \\$([\\d,]+(?:\\.\\d{1,2})?)",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern LOST = Pattern.compile(
			"BondJoules\\s*»\\s*You have lost \\$([\\d,]+(?:\\.\\d{1,2})?)",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern PUSH = Pattern.compile(
			"Neutralized! You have received your deposit back", Pattern.CASE_INSENSITIVE);
	private static final Pattern DOUBLED = Pattern.compile(
			"investment has been raised to \\$([\\d,]+(?:\\.\\d{1,2})?)",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern STARTED = Pattern.compile(
			"Starting a new experiment", Pattern.CASE_INSENSITIVE);

	private static Outcome last;
	private static long doubledStakeCents;

	private BjChat() {
	}

	public static void onMessage(String text) {
		if (text == null) {
			return;
		}
		// A fresh hand retires the previous result and the previous double.
		if (STARTED.matcher(text).find()) {
			last = null;
			doubledStakeCents = 0L;
			return;
		}
		Matcher doubled = DOUBLED.matcher(text);
		if (doubled.find()) {
			doubledStakeCents = Money.parseCents(doubled.group(1));
			return;
		}
		Matcher won = WON.matcher(text);
		if (won.find()) {
			last = new Outcome(Money.parseCents(won.group(1)), true, false);
			return;
		}
		Matcher lost = LOST.matcher(text);
		if (lost.find()) {
			last = new Outcome(Money.parseCents(lost.group(1)), false, false);
			return;
		}
		if (PUSH.matcher(text).find()) {
			last = new Outcome(0L, false, true);
		}
	}

	public static Outcome lastOutcome() {
		return last;
	}

	/**
	 * The stake after a double down, or 0. The window title keeps showing the original
	 * figure after doubling, so this is the only place the raised one is stated.
	 */
	public static long doubledStakeCents() {
		return doubledStakeCents;
	}

	/** The result belongs to the server we just left. */
	public static void reset() {
		last = null;
		doubledStakeCents = 0L;
	}
}
