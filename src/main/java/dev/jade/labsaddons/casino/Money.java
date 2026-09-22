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
	private static final long MILLION = 1_000_000L;
	private static final double MILLION_D = 1_000_000.0;
	/** Where {@link #compact} starts shortening, and where {@link #abbreviated} does. */
	private static final long COMPACT_FROM_DOLLARS = 10_000L;
	private static final long ABBREVIATE_FROM_DOLLARS = 1_000L;

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

	/**
	 * "$3,828.25", or "$6,125" when it is a round number of dollars.
	 *
	 * <p>A negative figure reads "−$305,062,313.11", not "$-305,062,313.11". Coinflip is the
	 * first game whose figures go below zero — a lifetime profit — and the sign belongs
	 * outside the currency, where a person would put it.
	 */
	public static String format(long amountCents) {
		String sign = amountCents < 0 ? "−" : "";
		long magnitude = Math.abs(amountCents);
		long dollars = magnitude / CENTS;
		int part = (int) (magnitude % CENTS);
		String text = sign + "$" + String.format(Locale.ROOT, "%,d", dollars);
		return part == 0 ? text : text + String.format(Locale.ROOT, ".%02d", part);
	}

	/**
	 * "$10.1k", for a column of figures where a full one will not fit. Whole dollars
	 * throughout: cents on some rows and not others reads as two different formats.
	 */
	public static String compact(long amountCents) {
		return shorten(amountCents, COMPACT_FROM_DOLLARS);
	}

	/**
	 * "$9.0k" where {@link #compact} would still spell out "$9,000".
	 *
	 * <p>For a column where every row has to read the same way: one row saying "$9,000"
	 * beside another saying "$10.0k" is two formats in one column, which is the thing
	 * compact() was supposed to stop. Below a thousand it still spells the figure out,
	 * because "$0.8k" is a worse answer than "$750".
	 */
	public static String abbreviated(long amountCents) {
		return shorten(amountCents, ABBREVIATE_FROM_DOLLARS);
	}

	private static String shorten(long amountCents, long fromDollars) {
		String sign = amountCents < 0 ? "−" : "";
		long dollars = Math.abs(amountCents) / CENTS;
		if (dollars < fromDollars) {
			return format(Math.round(amountCents / (double) CENTS) * CENTS);
		}
		if (dollars < MILLION) {
			return sign + "$" + String.format(Locale.ROOT, "%.1fk", dollars / 1_000.0);
		}
		return sign + "$" + String.format(Locale.ROOT, "%.1fm", dollars / MILLION_D);
	}

	public static long fromDollars(long dollars) {
		return dollars * CENTS;
	}
}
