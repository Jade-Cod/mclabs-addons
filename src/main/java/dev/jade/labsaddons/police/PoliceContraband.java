package dev.jade.labsaddons.police;

import dev.jade.labsaddons.prestige.PrestigeChem;
import dev.jade.labsaddons.prestige.PrestigeTracker;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The police side of the Progress widget. One chat line drives all of it:
 *
 * <pre>
 * MCLPD » You confiscated 3,392 chems fayebeeann and earned $15,264 in commission!
 * MCLPD » Earned 3,832 progress (x1.13) in confiscating contraband. Click to see your total progress.
 * Arrest » fayebeeann was arrested with 3392 contraband by Ophiliah.
 * </pre>
 *
 * <p>The middle line is the one worth reading: 3,392 × 1.13 = 3,832, so the raw chem
 * count and the rate are both already folded into the figure the server states. A
 * bounty chest posts the same line under the {@code Bounty »} prefix, with no rate and
 * a fraction ({@code Earned 976.32 progress}). A frisk that finds nothing — or the
 * "Small amount of contraband found (2)" pat-down — posts no progress line at all, and
 * so credits nothing, which is the server's own behaviour.
 *
 * <p>The figure feeds both the police prestige track and the Patrol Mastery
 * challenges, since they count the same contraband against different goals.
 */
public final class PoliceContraband {
	/**
	 * Prestige tracks are stored under the GUI's tier name minus its prefix:
	 * "✘ Police - Collect Contraband III" is kept as "Contraband III", which is what
	 * the HUD renders and what a pin is remembered by.
	 */
	public static final String TRACK_PREFIX = "Contraband ";

	/** Both prefixes, both with and without the "(x1.13)" rate. */
	private static final Pattern EARNED = Pattern.compile(
			"earned\\s+([\\d,]+(?:\\.\\d+)?)\\s+progress\\b.{0,20}?\\bin confiscating contraband",
			Pattern.CASE_INSENSITIVE);

	private PoliceContraband() {
	}

	/** @return true if a track or challenge moved, so the caller can persist the boards. */
	public static boolean onMessage(String text) {
		if (text == null) {
			return false;
		}
		Matcher earned = EARNED.matcher(text);
		if (!earned.find()) {
			return false;
		}
		double amount = parseNumber(earned.group(1));
		if (amount <= 0) {
			return false;
		}
		// ponytail: the Mastery challenges are credited with the same boosted figure as
		// prestige, which is unverified — a /mastery scrape either side of one arrest
		// settles it, and the scrape is the authority meanwhile.
		boolean changed = PatrolQuests.advance(amount);
		return advancePrestige(amount) || changed;
	}

	/** Whether a prestige row belongs to the police ladder rather than a chem. */
	public static boolean isTrack(String name) {
		return name != null && name.startsWith(TRACK_PREFIX);
	}

	/**
	 * Advances the tier being worked on.
	 *
	 * <p>The server runs one counter behind every unmet tier — III, IV, V and VI all
	 * read 404,725 against their own goals — so only the nearest one is tracked, and
	 * the next {@code /prestige} scrape hands over to the one after it.
	 */
	private static boolean advancePrestige(double amount) {
		for (PrestigeChem chem : PrestigeTracker.chems()) {
			if (isTrack(chem.chem()) && !chem.isComplete()) {
				return PrestigeTracker.advance(chem.chem(), amount);
			}
		}
		return false;
	}

	private static double parseNumber(String raw) {
		try {
			return Double.parseDouble(raw.replace(",", "").toLowerCase(Locale.ROOT));
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
