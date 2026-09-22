package dev.jade.labsaddons.crate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * puts that one back to roll #1. <b>Only that one.</b> The ladders are entirely independent in
 * both directions, confirmed in game: unboxing an Exceedingly Rare leaves the Super Rare and
 * Very Rare streaks where they were, and unboxing a Super Rare leaves Exceedingly Rare and Very
 * Rare where they were. The captures show the same thing from the other side — two of them a
 * session apart had Very Rare sitting at roll #1 while the two above it carried on climbing.
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

	/**
	 * How long after a roll another signal for one is taken to be the same roll.
	 *
	 * <p>A watched spin lands, then about half a second later the server says your odds went
	 * up. Both describe one roll. Six seconds is comfortably past that gap and comfortably
	 * short of the next spin, which cannot start until the player has opened another crate.
	 */
	private static final long SAME_ROLL_MS = 6_000L;

	/**
	 * A Crate Roll Booster's own words. The server ships four tiers of the voucher
	 * ({@code crate-roll-booster-0} through {@code -3}, one per amethyst bud in its resource
	 * pack) and the line is the only place the count is stated in a form worth parsing, so the
	 * number is read rather than looked up per tier.
	 */
	private static final Pattern BOOSTER = Pattern.compile(
			"^MCLabs » Your odds have been increased by ([0-9,]+) rolls?\\b",
			Pattern.CASE_INSENSITIVE);
	/**
	 * Most a booster may be believed to grant. No tier comes near it; the cap is here because
	 * this is a number read off chat and applied to a count the player has spent hundreds of
	 * keys building, and a line that says something absurd should move nothing.
	 */
	private static final int MAX_BOOST = 10_000;

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
	 * Which roll the next spin will be for this rarity — how long it has been dry — or 0 if the
	 * odds menu has never been opened for it and there is therefore nothing to count from.
	 */
	public static int roll(Map<String, Integer> rolls, CrateRarity rarity) {
		if (rolls == null || rarity == null) {
			return 0;
		}
		Integer at = rolls.get(rarity.name());
		return at == null || at < 1 ? 0 : at;
	}

	/**
	 * How many keys have gone in since this rarity last landed: nought the moment it does, one
	 * after the next crate, and so on.
	 *
	 * <p>One less than the roll number the server uses, which counts the roll <em>about to</em>
	 * happen — after a win the next roll is #1, and no keys have been spent on the drought yet.
	 * The chance shown beside this is that upcoming roll's, so the pair reads as what the
	 * drought has cost so far and what the next key is worth.
	 */
	public static int dry(int roll) {
		return Math.max(0, roll - 1);
	}

	/**
	 * How many rolls a Crate Roll Booster just granted, or 0 for any other line.
	 *
	 * <p>Anchored to the server's prefix rather than found anywhere in the line, so a player
	 * quoting the message in chat cannot move anybody's counters.
	 */
	public static int boostedRolls(String line) {
		if (line == null) {
			return 0;
		}
		Matcher booster = BOOSTER.matcher(line.trim());
		if (!booster.find()) {
			return 0;
		}
		try {
			int rolls = Integer.parseInt(booster.group(1).replace(",", ""));
			return rolls > 0 && rolls <= MAX_BOOST ? rolls : 0;
		} catch (NumberFormatException absurd) {
			return 0;
		}
	}

	/**
	 * The counters after a booster granting {@code rolls}, or null if none of them moved.
	 *
	 * <p>Every ladder goes forward together — the voucher's own lore says it jacks up "your
	 * Rare+ crate odds", not one rarity's — and it is not a roll: no key was spent, nothing was
	 * unboxed, and the server sends no odds-went-up line after it. It simply puts each counter
	 * where it would have been that many crates later, which is what the odds menu will say the
	 * next time it is opened.
	 */
	public static Map<String, Integer> boosted(Map<String, Integer> rolls, int granted) {
		if (granted <= 0 || granted > MAX_BOOST) {
			return null;
		}
		Map<String, Integer> next = copy(rolls);
		boolean moved = false;
		for (CrateRarity rarity : TRACKED) {
			Integer at = next.get(rarity.name());
			// Never anchored, so there is nothing for the booster to move forward from.
			if (at == null || at < 1) {
				continue;
			}
			next.put(rarity.name(), at + granted);
			moved = true;
		}
		return moved ? next : null;
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
	 * Which roll the player is on, read off a page of the odds menu, or 0 if this page cannot
	 * say.
	 *
	 * <p>The menu draws a roll already spent with a different head from one still ahead — the
	 * spent ones an opened crate, the rest a closed one — so the page marks the boundary even
	 * though it labels nothing. The spent rolls are a prefix, and the first head past them is
	 * the roll about to happen.
	 *
	 * <p>Which side is which never has to be decided: only the change matters. A page with no
	 * change on it is one the player has walked back or forward to, or the rare case of their
	 * being on its very first roll, and it says nothing rather than guessing. So does a page
	 * showing three kinds of head, which is not the shape this reads and is the only way a
	 * server change could turn this into a wrong number instead of no number.
	 *
	 * @param page each head's roll number against a key for how that head is drawn; anything
	 *             that differs between a spent roll and one ahead will do, since the keys are
	 *             only ever compared with each other
	 */
	public static int currentRoll(Map<Integer, ?> page) {
		if (page == null || page.size() < 2) {
			return 0;
		}
		TreeMap<Integer, ?> byRoll = new TreeMap<>(page);
		Object spent = byRoll.firstEntry().getValue();
		Object ahead = null;
		int current = 0;
		for (Map.Entry<Integer, ?> head : byRoll.entrySet()) {
			Object mark = head.getValue();
			if (Objects.equals(mark, spent)) {
				// A spent head after an unspent one: not the two runs this reads.
				if (ahead != null) {
					return 0;
				}
				continue;
			}
			if (ahead == null) {
				ahead = mark;
				current = head.getKey();
			} else if (!Objects.equals(mark, ahead)) {
				return 0;
			}
		}
		return current;
	}

	/**
	 * The counters after the odds menu said the player is on {@code roll}, or null if that is
	 * where they already were.
	 */
	public static Map<String, Integer> anchored(Map<String, Integer> rolls, CrateRarity rarity,
			int roll) {
		if (rarity == null || roll < 1 || roll(rolls, rarity) == roll) {
			return null;
		}
		Map<String, Integer> next = copy(rolls);
		next.put(rarity.name(), roll);
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
	 * it is fired by the roll rather than by the result. The server sends it as the whole line
	 * with the bolt leading, and it is checked that way: a player message always carries their
	 * rank and name first — bracketed too, which is why the bracket alone is not enough — so
	 * quoting it in chat cannot count somebody a roll.
	 *
	 * <p>No voter crate capture has it, which is why a voter roll counts toward nothing here.
	 * Nor does a Crate Roll Booster, which sends no such line — see {@link #boostedRolls}.
	 */
	public static boolean isPityTick(String line) {
		if (line == null) {
			return false;
		}
		String lower = line.trim().toLowerCase(Locale.ROOT);
		return lower.startsWith("[\u26a1")
				&& lower.contains("odds have been jacked-up")
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
