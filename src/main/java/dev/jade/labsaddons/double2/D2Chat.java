package dev.jade.labsaddons.double2;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The one thing the menu cannot tell us: what your bet actually paid.
 *
 * <p>The settled banner only ever said "Nobody profited." in the rounds captured, and
 * the credited figure is not the face multiplier — $1,000 on CQL paid $1,940, which is
 * either 3% off the return or 6% off the profit. Rather than pick one and be wrong, the
 * overlay shows the server's own number, read from its own words.
 */
public final class D2Chat {
	/** What the last settled round did to your money. */
	public record Outcome(Lab lab, long amount, boolean won) {
	}

	private static final Pattern SETTLE = Pattern.compile(
			"your investment in ([A-Za-z]{3}) (profited|lost) you \\$([\\d,]+)",
			Pattern.CASE_INSENSITIVE);
	private static final String ROUND_OPEN = "market now open";

	private static Outcome last;

	private D2Chat() {
	}

	public static void onMessage(String text) {
		if (text == null) {
			return;
		}
		// A fresh round retires the previous result, so a stale payout can never be
		// shown against the wrong spin.
		if (text.toLowerCase(Locale.ROOT).contains(ROUND_OPEN)) {
			last = null;
			return;
		}
		Matcher matcher = SETTLE.matcher(text);
		if (!matcher.find()) {
			return;
		}
		// The win line lower-cases the lab code ("in cql") where the loss line does not
		// ("in EOL"), so this is matched case-insensitively on purpose.
		Lab lab = Lab.fromText(matcher.group(1));
		if (lab != null) {
			last = new Outcome(lab, D2Reader.parseMoney(matcher.group(3)),
					matcher.group(2).equalsIgnoreCase("profited"));
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
