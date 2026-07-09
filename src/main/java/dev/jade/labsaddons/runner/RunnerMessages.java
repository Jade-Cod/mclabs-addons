package dev.jade.labsaddons.runner;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure parsing of runner job chat lines — no Minecraft or FabricLoader deps, so
 * it unit-tests directly. {@link RunnerTracker} uses it to turn a raw chat line
 * into a typed event. Real MCLabs formats (supplier side — "your" job):
 * <ul>
 *   <li>Posted: {@code MCLabs » Runner job posted: Nx Drug-p-p-p at P%.}</li>
 *   <li>Taken: {@code Runner » Player has taken your runner job (Drug-p-p-p xN)}</li>
 *   <li>Completed: {@code Runner » Player has completed your runner job, you've earned $X.}</li>
 *   <li>Failed: {@code Runner » Player failed your runner job! (Drug-p-p-p xN)}</li>
 * </ul>
 * Posting increments the outstanding count; a job is then <b>taken</b> (claimed)
 * before it is completed or failed. The taken line carries the runner + drug +
 * quantity, so a completion (which carries only the payout) inherits the drug/qty
 * and its duration from the matching taken. The {@code MCLabs »}-prefixed posted
 * line is distinct from the runner-duty "New job posted!" board spam, which does
 * not match any pattern here.
 */
public final class RunnerMessages {
	private static final Pattern POSTED = Pattern.compile(
			"MCLabs.*Runner job posted:", Pattern.CASE_INSENSITIVE);
	private static final Pattern TAKEN = Pattern.compile(
			"([A-Za-z0-9_]{3,16}) has taken your runner job", Pattern.CASE_INSENSITIVE);
	private static final Pattern COMPLETED = Pattern.compile(
			"([A-Za-z0-9_]{3,16}) (?:has )?completed your runner job.*you've earned \\$([0-9,.]+[kmbKMB]?)",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern FAILED = Pattern.compile(
			"([A-Za-z0-9_]{3,16}) failed your runner job", Pattern.CASE_INSENSITIVE);

	/** Trailing "(Drug-p-p-p xN)" detail on the taken/failed lines. */
	private static final Pattern PAREN = Pattern.compile("\\(([^)]*)\\)");
	/** "Papwartinide-2-2-2 x2,176" -> drug, qty. */
	private static final Pattern DETAIL = Pattern.compile("(.+?)\\s+x([\\d,]+)", Pattern.CASE_INSENSITIVE);

	public enum Type {
		POSTED, TAKEN, COMPLETED, FAILED
	}

	/**
	 * A parsed runner event: its {@link Type}, the runner's name (empty for
	 * {@link Type#POSTED}), money earned (only on {@link Type#COMPLETED}), and the
	 * drug + quantity (on {@link Type#TAKEN}/{@link Type#FAILED}; empty/0 otherwise).
	 */
	public record Event(Type type, String runner, double value, String drug, int qty) {
	}

	private RunnerMessages() {
	}

	/** Parse one chat line into an {@link Event}, or {@code null} if it isn't a runner event. */
	public static Event parse(String text) {
		if (POSTED.matcher(text).find()) {
			return new Event(Type.POSTED, "", 0.0, "", 0);
		}
		Matcher taken = TAKEN.matcher(text);
		if (taken.find()) {
			String detail = detailOf(text);
			return new Event(Type.TAKEN, taken.group(1), 0.0, drugOf(detail), qtyOf(detail));
		}
		Matcher completed = COMPLETED.matcher(text);
		if (completed.find()) {
			return new Event(Type.COMPLETED, completed.group(1), parseMoney(completed.group(2)), "", 0);
		}
		Matcher failed = FAILED.matcher(text);
		if (failed.find()) {
			String detail = detailOf(text);
			return new Event(Type.FAILED, failed.group(1), 0.0, drugOf(detail), qtyOf(detail));
		}
		return null;
	}

	/** Content of the first "(...)" group in the line, or "". */
	private static String detailOf(String text) {
		Matcher m = PAREN.matcher(text);
		return m.find() ? m.group(1) : "";
	}

	private static String drugOf(String detail) {
		Matcher m = DETAIL.matcher(detail);
		return m.find() ? m.group(1).trim() : detail.trim();
	}

	private static int qtyOf(String detail) {
		Matcher m = DETAIL.matcher(detail);
		return m.find() ? parseQty(m.group(2)) : 0;
	}

	private static int parseQty(String raw) {
		try {
			return Integer.parseInt(raw.replace(",", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** "$25,200" / "$12k" / "$2.1m" / "$0" -> a plain amount; 0 on parse failure. */
	static double parseMoney(String value) {
		String clean = value.toLowerCase(Locale.ROOT).replace(",", "");
		try {
			if (clean.endsWith("k")) {
				return Double.parseDouble(clean.substring(0, clean.length() - 1)) * 1_000;
			}
			if (clean.endsWith("m")) {
				return Double.parseDouble(clean.substring(0, clean.length() - 1)) * 1_000_000;
			}
			if (clean.endsWith("b")) {
				return Double.parseDouble(clean.substring(0, clean.length() - 1)) * 1_000_000_000;
			}
			return Double.parseDouble(clean);
		} catch (NumberFormatException e) {
			return 0.0;
		}
	}
}
