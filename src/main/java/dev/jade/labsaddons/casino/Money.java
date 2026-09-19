package dev.jade.labsaddons.casino;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Money in whole cents.
 *
 * <p>Mines pays fractions — the captured round offered "$3,828.25" and "$10,062.5" — so
 * the board cannot hold these figures as whole dollars. Cents are exact, and the server
 * never states more than two decimals.
 */
public final class Money {
	private static final Pattern AMOUNT = Pattern.compile("\\$?([\\d,]+)(?:\\.(\\d{1,2}))?");
	private static final int CENTS = 100;

	private Money() {
	}

	/**
	 * The first money figure in {@code text}, in cents, or 0 when there is none.
	 *
	 * <p>0 rather than an exception because every caller is reading a live GUI: a slot
	 * mid-update states nothing, and that is not an error.
	 */
	public static long parseCents(String text) {
		if (text == null) {
			return 0L;
		}
		Matcher matcher = AMOUNT.matcher(text);
		return matcher.find() ? cents(matcher) : 0L;
	}

	/** The figure captured by a caller's own pattern, whose group 1 is the whole amount. */
	public static long parseCents(Matcher matched, int group) {
		Matcher matcher = AMOUNT.matcher(matched.group(group));
		return matcher.find() ? cents(matcher) : 0L;
	}

	private static long cents(Matcher matcher) {
		long dollars;
		try {
			dollars = Long.parseLong(matcher.group(1).replace(",", ""));
		} catch (NumberFormatException e) {
			return 0L;
		}
		String fraction = matcher.group(2);
		if (fraction == null) {
			return dollars * CENTS;
		}
		// "5" is five tenths, not five cents.
		int part = Integer.parseInt(fraction.length() == 1 ? fraction + "0" : fraction);
		return dollars * CENTS + part;
	}

	/** "$3,828.25", or "$6,125" when it is a round number of dollars. */
	public static String format(long amountCents) {
		long dollars = amountCents / CENTS;
		int part = (int) Math.abs(amountCents % CENTS);
		String text = "$" + String.format(Locale.ROOT, "%,d", dollars);
		return part == 0 ? text : text + String.format(Locale.ROOT, ".%02d", part);
	}

	/**
	 * "$10.1k", for a column of figures where a full one will not fit. Whole dollars
	 * throughout: cents on some rows and not others reads as two different formats.
	 */
	public static String compact(long amountCents) {
		long dollars = amountCents / CENTS;
		if (dollars < 10_000) {
			return format(Math.round(amountCents / (double) CENTS) * CENTS);
		}
		if (dollars < 1_000_000) {
			return "$" + String.format(Locale.ROOT, "%.1fk", dollars / 1_000.0);
		}
		return "$" + String.format(Locale.ROOT, "%.1fm", dollars / 1_000_000.0);
	}

	public static long fromDollars(long dollars) {
		return dollars * CENTS;
	}
}
