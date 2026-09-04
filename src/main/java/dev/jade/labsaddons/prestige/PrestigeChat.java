package dev.jade.labsaddons.prestige;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads prestige figures out of the chat messages that carry them.
 *
 * <p>Both sources put their numbers in hover tooltips rather than in the visible line,
 * so both go through {@link TextHovers}:
 *
 * <ul>
 *   <li>{@code /prestige progress} prints a header, one message <em>per chem</em>, then a
 *       footer. A row shows only {@code [||||] Wheatium ✔} but hovers to
 *       {@code Wheatium: 0/806,400}, or to {@code Wheatium: Complete} once finished.
 *       That is the authoritative sync.</li>
 *   <li>A sale prints {@code Earned prestige progress for Cactium and Potatium. (1.3x rate)},
 *       whose hover gives the exact amount each base chem earned
 *       ({@code Cactium x8,273 / Potatium x5,516}).</li>
 * </ul>
 *
 * <p>Nothing here computes progress. The observed pair 8,273 / 5,516 for one sale is not
 * reproducible from count, rate and purity under any single rounding rule — the server
 * rounds per-source in a way the client cannot see — so the stated number is the only
 * correct one to use.
 *
 * <p>Because each row is its own message, and a row's own text names no command, the
 * listing is read as a window: the header arms it and the footer closes it. Matching
 * rows on their own shape instead would risk mistaking any future message that happened
 * to hover a chem's figures for a sync.
 */
public final class PrestigeChat {
	/** Opens the {@code /prestige progress} listing. */
	private static final String LIST_HEADER = "your prestige progress";
	/** Closes it: "Hover over a chem to see full progress amount." */
	private static final String LIST_FOOTER = "hover over a chem";
	/** Present on the sell confirmation's prestige line. */
	private static final String SALE_MARKER = "earned prestige progress";

	/**
	 * Upper bound on the window, in case the footer never arrives. Fourteen chems today,
	 * with slack for other players' chat landing between rows.
	 */
	private static final int LIST_WINDOW = 60;

	/** "Wheatium: 0/806,400" — any further tooltip lines are ignored. */
	private static final Pattern LISTED = Pattern.compile(
			"([A-Za-z]+)\\s*:\\s*([\\d,]+(?:\\.\\d+)?)\\s*/\\s*([\\d,]+(?:\\.\\d+)?)");

	/** "Pumpkonium: Complete" — a finished track, which states no figures at all. */
	private static final Pattern UNLOCKED = Pattern.compile(
			"([A-Za-z]+)\\s*:\\s*Complete\\b", Pattern.CASE_INSENSITIVE);

	/**
	 * "Cactium x8,273". Matched with {@code find()} rather than anchored per line: the
	 * tooltip's line breaks do not survive flattening to a string, so the entries may
	 * arrive run together.
	 */
	private static final Pattern EARNED = Pattern.compile("([A-Za-z]+)\\s*x\\s*([\\d,]+(?:\\.\\d+)?)");

	/** Messages left in the listing window; 0 when not reading one. */
	private static int remaining;
	/** Whether a row has merged since the window opened and still needs persisting. */
	private static boolean pending;

	private PrestigeChat() {
	}

	/**
	 * Handles one incoming chat message.
	 *
	 * @return true if prestige state changed and should be persisted. A listing reports
	 *         this once, when its window closes — saving is a synchronous file write, and
	 *         a fourteen-row sync must not mean fourteen of them.
	 */
	public static boolean onMessage(Component message) {
		if (message == null || message.getString() == null) {
			return false;
		}
		String lower = message.getString().toLowerCase(Locale.ROOT);

		if (lower.contains(LIST_HEADER)) {
			// The header itself carries no hover; it only opens the window.
			remaining = LIST_WINDOW;
			pending = false;
			return false;
		}
		// Checked ahead of the window so a sale is never swallowed by a listing that
		// somehow failed to close.
		if (lower.contains(SALE_MARKER)) {
			return applyEarned(parseEarned(TextHovers.tooltips(message)));
		}
		if (remaining <= 0) {
			return false;
		}

		remaining--;
		boolean closing = lower.contains(LIST_FOOTER);
		if (!closing) {
			List<PrestigeChem> row = parseListing(TextHovers.tooltips(message));
			if (!row.isEmpty()) {
				PrestigeTracker.merge(row);
				pending = true;
			}
		}
		if (!closing && remaining > 0) {
			return false;
		}
		boolean save = pending;
		remaining = 0;
		pending = false;
		return save;
	}

	/** The chems a listing row's tooltips describe — with figures, or merely "Complete". */
	static List<PrestigeChem> parseListing(List<String> tooltips) {
		List<PrestigeChem> found = new ArrayList<>();
		for (String tooltip : tooltips) {
			Matcher figures = LISTED.matcher(tooltip);
			if (figures.find()) {
				double target = parseNumber(figures.group(3));
				if (target > 0) {
					found.add(new PrestigeChem(figures.group(1), parseNumber(figures.group(2)), target));
				}
				continue;
			}
			Matcher done = UNLOCKED.matcher(tooltip);
			if (done.find()) {
				found.add(PrestigeChem.unlocked(done.group(1)));
			}
		}
		return found;
	}

	/** Chem name to amount earned, as a sale's hover states it. */
	static List<Gain> parseEarned(List<String> tooltips) {
		List<Gain> found = new ArrayList<>();
		for (String tooltip : tooltips) {
			Matcher matched = EARNED.matcher(tooltip);
			while (matched.find()) {
				double amount = parseNumber(matched.group(2));
				if (amount > 0) {
					found.add(new Gain(matched.group(1), amount));
				}
			}
		}
		return found;
	}

	/** One base chem's share of a sale. */
	record Gain(String chem, double amount) {
	}

	private static boolean applyEarned(List<Gain> gains) {
		boolean changed = false;
		for (Gain gain : gains) {
			changed |= PrestigeTracker.advance(gain.chem(), gain.amount());
		}
		return changed;
	}

	/** Strips digit grouping ("806,400" -> 806400.0). */
	private static double parseNumber(String raw) {
		try {
			return Double.parseDouble(raw.replace(",", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** Drops a half-read listing, so a window cannot survive into the next connection. */
	public static void reset() {
		remaining = 0;
		pending = false;
	}
}
