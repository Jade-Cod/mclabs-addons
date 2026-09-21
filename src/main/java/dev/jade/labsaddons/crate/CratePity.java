package dev.jade.labsaddons.crate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The server's pity ladder: how likely each rarity is on your next crate roll, and how many
 * rolls it has been since that rarity last paid out.
 *
 * <p>MCLabs states this outright — every crate reward's lore ends {@code "┃ Click to view odds
 * of each roll."}, and that opens a paginated table of {@code "Roll #190"} / {@code "Chance:
 * 0.277%"} heads, nine to a page. The whole of it was scraped for three rarities (999, 1044
 * and 1215 consecutive rolls) and every single value is reproduced exactly by the closed forms
 * in {@link #chance}, to the third decimal the server itself prints. Nothing here is fitted or
 * approximate; the tests hold the server's own figures.
 *
 * <p>The counters are per rarity and shared across every crate — the menu's own words are
 * "your odds of unboxing a Rare+ item <em>in all crates</em> increases" — and winning a rarity
 * puts that one back to roll #1. Only that one: two captures a session apart had Very Rare
 * sitting at roll #1 while Super Rare and Exceedingly Rare carried on climbing, which is what
 * a ladder rolled from the top down has to look like. Nothing resets a rarity above it.
 *
 * <p>Minecraft-free on purpose, like {@link CrateSpin}, so the curves can be checked against
 * the captures. The counters are handed in and out as a plain map, which is the config field
 * they live in; the one piece of state kept here is the clock in {@link #isNewRoll}, which is
 * session-only and exists so a roll is never counted twice.
 */
public final class CratePity {
	/**
	 * The rarities the board shows and counts. The three that are worth waiting for — Rare
	 * lands often enough that its counter is almost always 1 or 2, and the two below it are
	 * flat (33% and 100%) and have no pity at all.
	 */
	public static final List<CrateRarity> TRACKED = List.of(
			CrateRarity.EXCEEDINGLY_RARE, CrateRarity.SUPER_RARE, CrateRarity.VERY_RARE);

	/** Rolls to a page of the server's odds menu. */
	public static final int PAGE = 9;

	/**
	 * How long after a roll another signal for one is taken to be the same roll.
	 *
	 * <p>A watched spin lands, then about half a second later the server says your odds went
	 * up. Both describe one roll. Six seconds is comfortably past that gap and comfortably
	 * short of the next spin, which cannot start until the player has opened another crate.
	 */
	private static final long SAME_ROLL_MS = 6_000L;

	/** Where the Exceedingly Rare curve changes shape. See {@link #exceedingly}. */
	private static final int ER_EARLY_END = 11;
	private static final int ER_CLIFF = 250;

	private static long lastRollMs;

	private CratePity() {
	}

	// --- the curves ----------------------------------------------------------

	/**
	 * The server's own chance, as a percentage, of this rarity landing on roll {@code roll}.
	 *
	 * <p>Rare and Very Rare are the same square root three times apart; Super Rare is a
	 * shallower root of the same shape. Exceedingly Rare is three curves, which is why one
	 * page of it looks like a flat line:
	 *
	 * <ul>
	 *   <li>rolls 2–11 climb hard, {@code 0.06·(N−1)^0.6}, 0.06% to 0.239%;</li>
	 *   <li>rolls 12–250 barely move, {@code 0.213·(N−1)^0.05} — 239 rolls to go from
	 *       0.24% to 0.281%;</li>
	 *   <li>roll 251 onward is {@code 0.000214·(N−1)^1.3}, which finally pays. The switch is
	 *       the one non-monotonic step in all 1215 captured values: 0.281% drops to 0.280%.</li>
	 * </ul>
	 *
	 * @param roll which roll since this rarity last landed, counting the next one as 1
	 */
	public static double chance(CrateRarity rarity, int roll) {
		int n = Math.max(1, roll);
		return switch (rarity) {
			case COMMON -> 100d;
			case UNCOMMON -> 33d;
			case RARE -> n == 1 ? 2d : 3d * Math.sqrt(n - 1);
			case VERY_RARE -> n == 1 ? 0.66d : Math.sqrt(n - 1);
			case SUPER_RARE -> n == 1 ? 0.2d : 0.33d * Math.pow(n - 1, 0.4d);
			case EXCEEDINGLY_RARE -> exceedingly(n);
		};
	}

	private static double exceedingly(int n) {
		if (n == 1) {
			return 0.02d;
		}
		if (n <= ER_EARLY_END) {
			return 0.06d * Math.pow(n - 1, 0.6d);
		}
		if (n <= ER_CLIFF) {
			return 0.213d * Math.pow(n - 1, 0.05d);
		}
		return 0.000214d * Math.pow(n - 1, 1.3d);
	}

	/**
	 * A chance written the way the server writes it: three decimals with the trailing zeros
	 * taken off, so 0.660 reads "0.66%" and 2.000 reads "2%".
	 */
	public static String format(double chance) {
		String text = String.format(Locale.ROOT, "%.3f", chance);
		if (text.indexOf('.') >= 0) {
			while (text.endsWith("0")) {
				text = text.substring(0, text.length() - 1);
			}
			if (text.endsWith(".")) {
				text = text.substring(0, text.length() - 1);
			}
		}
		return text + "%";
	}

	// --- the counters --------------------------------------------------------

	/**
	 * Which roll the next spin will be for this rarity, or 0 if the odds menu has never been
	 * opened for it and there is therefore nothing to count from.
	 *
	 * <p>A lower bound, never an overstatement: see {@link #anchored}.
	 */
	public static int roll(Map<String, Integer> rolls, CrateRarity rarity) {
		if (rolls == null || rarity == null) {
			return 0;
		}
		Integer at = rolls.get(rarity.name());
		return at == null || at < 1 ? 0 : at;
	}

	/**
	 * The counters after one crate roll, or null if none of them moved.
	 *
	 * @param won the rarity that landed, whose counter goes back to one, or null for a roll
	 *            whose result we did not see — which the server's own chat line tells us was
	 *            at least not an Exceedingly Rare
	 */
	public static Map<String, Integer> rolled(Map<String, Integer> rolls, CrateRarity won) {
		Map<String, Integer> next = copy(rolls);
		boolean moved = false;
		for (CrateRarity rarity : TRACKED) {
			Integer at = next.get(rarity.name());
			// Never anchored: counting up from a number we do not have would invent one.
			if (at == null || at < 1) {
				continue;
			}
			next.put(rarity.name(), rarity == won ? 1 : at + 1);
			moved = true;
		}
		return moved ? next : null;
	}

	/**
	 * The counters after the odds menu opened on the page beginning at {@code firstOnPage},
	 * or null if it told us nothing new.
	 *
	 * <p>The menu opens on the page holding your current roll and marks none of the nine, so
	 * this can only pin the count to a window that wide. It takes the bottom of the window,
	 * which makes every count here a floor and every chance drawn from one an understatement —
	 * the right direction to be wrong in, and worth at most 0.01% anywhere past roll 30.
	 *
	 * <p>A count already inside the page is left alone: it is as good as anything the page
	 * could say, and better, since it has real rolls behind it.
	 */
	public static Map<String, Integer> anchored(Map<String, Integer> rolls, CrateRarity rarity,
			int firstOnPage) {
		if (rarity == null || firstOnPage < 1) {
			return null;
		}
		int at = roll(rolls, rarity);
		if (at >= firstOnPage && at <= firstOnPage + PAGE - 1) {
			return null;
		}
		Map<String, Integer> next = copy(rolls);
		next.put(rarity.name(), firstOnPage);
		return next;
	}

	private static Map<String, Integer> copy(Map<String, Integer> rolls) {
		return rolls == null ? new LinkedHashMap<>() : new LinkedHashMap<>(rolls);
	}

	// --- telling one roll from two -------------------------------------------

	/**
	 * Whether a roll signalled now is one we have not already counted.
	 *
	 * <p>Two things announce the same roll: the spin landing on the board, which knows which
	 * rarity won, and the server's chat line a moment later, which does not. The board is
	 * believed and the chat line is the fallback for a crate opened with the board off or the
	 * animation elsewhere.
	 */
	public static boolean isNewRoll(long nowMs) {
		return lastRollMs == 0L || nowMs - lastRollMs >= SAME_ROLL_MS
				|| nowMs < lastRollMs;
	}

	/** Records that a roll has been counted, so the signal that follows it is not. */
	public static void counted(long nowMs) {
		lastRollMs = nowMs;
	}

	/** Visible for testing: forget what was counted, so each case starts clean. */
	static void forget() {
		lastRollMs = 0L;
	}

	// --- reading the server's odds menu --------------------------------------

	/**
	 * Whether this chat line is the server announcing that a crate roll just happened.
	 *
	 * <p>{@code "[⚡ Your Exceedingly Rare odds have been JACKED-UP! ⚡]"}, about half a second
	 * after the unbox line, in all ten captured spins — including the two that won a Rare, so
	 * it is fired by the roll rather than by the result. Matched on the words rather than the
	 * whole line so the brackets and the symbols either side can change without silencing it.
	 *
	 * <p>No voter crate capture has it, which is why a voter roll counts toward nothing here.
	 */
	public static boolean isPityTick(String line) {
		if (line == null) {
			return false;
		}
		String lower = line.toLowerCase(Locale.ROOT);
		return lower.contains("odds have been jacked-up")
				&& lower.contains(CrateRarity.EXCEEDINGLY_RARE.label().toLowerCase(Locale.ROOT));
	}

	/**
	 * The rarity whose odds table this menu is, or null for any other menu.
	 *
	 * <p>The title is {@code "Your Exceedingly Rare odds"} — the rarity in its own colour
	 * between two grey words.
	 */
	public static CrateRarity oddsMenuRarity(String title) {
		if (title == null) {
			return null;
		}
		String trimmed = title.trim();
		String lower = trimmed.toLowerCase(Locale.ROOT);
		// Long enough for the two words and something between them: "Your odds" satisfies both
		// ends at once and would otherwise ask for a substring that runs backwards.
		if (!lower.startsWith("your ") || !lower.endsWith(" odds") || trimmed.length() <= 10) {
			return null;
		}
		return CrateRarity.fromLabel(trimmed.substring(5, trimmed.length() - 5));
	}

	/**
	 * The roll a {@code "Roll #190"} head stands for, or 0 for anything else.
	 *
	 * <p>Deliberately strict about the whole name: the same menu holds {@code "Previous Page
	 * (21)"} and {@code "Next Page (23)"} heads whose lore reads {@code "Rolls #181 - #189"},
	 * and taking a number off either would put the anchor a page out.
	 */
	public static int rollNumber(String name) {
		if (name == null) {
			return 0;
		}
		String trimmed = name.trim();
		if (!trimmed.startsWith("Roll #")) {
			return 0;
		}
		String digits = trimmed.substring("Roll #".length());
		if (digits.isEmpty()) {
			return 0;
		}
		for (int i = 0; i < digits.length(); i++) {
			if (digits.charAt(i) < '0' || digits.charAt(i) > '9') {
				return 0;
			}
		}
		try {
			return Integer.parseInt(digits);
		} catch (NumberFormatException overflow) {
			return 0;
		}
	}
}
