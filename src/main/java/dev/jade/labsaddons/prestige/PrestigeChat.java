package dev.jade.labsaddons.prestige;

import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads prestige figures out of the two chat messages that carry them.
 *
 * <p>Both put their numbers in hover tooltips rather than in the visible line, so both
 * go through {@link TextHovers}:
 *
 * <ul>
 *   <li>{@code /prestige progress} prints a row per chem — visibly just
 *       {@code [||||] Wheatium (0%)}, but each row hovers to {@code Wheatium: 0/806,400}.
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
 * <p>Both messages are gated on their visible text before any hover is read, so an
 * unrelated message that happens to carry a similarly shaped tooltip cannot be mistaken
 * for either.
 */
public final class PrestigeChat {
	/** Header of the {@code /prestige progress} listing. */
	private static final String LIST_MARKER = "your prestige progress";
	/** Present on the sell confirmation's prestige line. */
	private static final String SALE_MARKER = "earned prestige progress";

	/** "Wheatium: 0/806,400" — the second line, "(0% supplier)", is not used. */
	private static final Pattern LISTED = Pattern.compile(
			"([A-Za-z]+)\\s*:\\s*([\\d,]+(?:\\.\\d+)?)\\s*/\\s*([\\d,]+(?:\\.\\d+)?)");

	/**
	 * "Cactium x8,273". Matched with {@code find()} rather than anchored per line: the
	 * tooltip's line breaks do not survive flattening to a string, so the entries may
	 * arrive run together.
	 */
	private static final Pattern EARNED = Pattern.compile("([A-Za-z]+)\\s*x\\s*([\\d,]+(?:\\.\\d+)?)");

	private PrestigeChat() {
	}

	/**
	 * Handles one incoming chat message.
	 *
	 * @return true if prestige state changed and should be persisted.
	 */
	public static boolean onMessage(Text message) {
		if (message == null) {
			return false;
		}
		String visible = message.getString();
		if (visible == null) {
			return false;
		}
		String lower = visible.toLowerCase(Locale.ROOT);
		if (lower.contains(LIST_MARKER)) {
			List<PrestigeChem> listed = parseListing(TextHovers.tooltips(message));
			if (listed.isEmpty()) {
				return false;
			}
			PrestigeTracker.merge(listed);
			return true;
		}
		if (lower.contains(SALE_MARKER)) {
			return applyEarned(parseEarned(TextHovers.tooltips(message)));
		}
		return false;
	}

	/** The chems a {@code /prestige progress} listing's tooltips describe. */
	static List<PrestigeChem> parseListing(List<String> tooltips) {
		List<PrestigeChem> found = new ArrayList<>();
		for (String tooltip : tooltips) {
			Matcher matched = LISTED.matcher(tooltip);
			if (!matched.find()) {
				continue;
			}
			double target = parseNumber(matched.group(3));
			if (target <= 0) {
				continue;
			}
			found.add(new PrestigeChem(matched.group(1), parseNumber(matched.group(2)), target));
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
}
