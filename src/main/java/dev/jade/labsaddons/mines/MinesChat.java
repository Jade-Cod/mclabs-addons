package dev.jade.labsaddons.mines;

import dev.jade.labsaddons.casino.Money;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the last Mines game paid, read from the server's own words.
 *
 * <p>The menu stops offering a figure the moment the game ends — the Cash Out item
 * becomes a plain pane — but the screen stays up for another three seconds. Chat is the
 * only thing still stating the result during those seconds, so it is what the board shows.
 */
public final class MinesChat {
	/** What the last finished game did to your money. */
	public record Outcome(long amountCents, boolean won) {
	}

	private static final Pattern WON = Pattern.compile(
			"Mines\\s*»\\s*You won \\$([\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);
	private static final Pattern LOST = Pattern.compile(
			"Mines\\s*»\\s*You have lost \\$([\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);
	private static final Pattern STARTED = Pattern.compile(
			"Mines\\s*»\\s*Starting a new game", Pattern.CASE_INSENSITIVE);

	private static Outcome last;

	private MinesChat() {
	}

	public static void onMessage(String text) {
		if (text == null) {
			return;
		}
		// A fresh game retires the previous result, so a stale payout can never be shown
		// against the wrong grid.
		if (STARTED.matcher(text).find()) {
			last = null;
			return;
		}
		Matcher won = WON.matcher(text);
		if (won.find()) {
			last = new Outcome(Money.parseCents(won.group(1)), true);
			return;
		}
		Matcher lost = LOST.matcher(text);
		if (lost.find()) {
			last = new Outcome(Money.parseCents(lost.group(1)), false);
		}
	}

	public static Outcome lastOutcome() {
		return last;
	}

	/** The result belongs to the server we just left. */
	public static void reset() {
		last = null;
	}
}
