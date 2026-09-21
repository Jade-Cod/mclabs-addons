package dev.jade.labsaddons.crate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a voter crate's rewards are worth, learned from the crate's own odds menu.
 *
 * <p>A voter crate has no rarity tiers at all — no coloured panes, no "Rarity:" lore, nothing
 * the roll screen can be read for. The only statement of how rare anything is lives in the
 * menu you get from punching the crate, as {@code "┃ Chance: 7.6%"} on each of its
 * twenty-seven rewards. So this is scraped once when the player looks at it and remembered,
 * which is what lets the three-way choice say which of its three draws is the rare one.
 *
 * <p>Pure and Minecraft-free: the reader hands it names and lore lines it has already
 * flattened. Nothing here models the odds — {@link #parseChance} only reads the number the
 * server printed.
 */
public final class VoteOdds {
	/** The crates this applies to, by menu title. */
	private static final String VOTER = "Voter Crate";
	private static final String DELUXE_VOTER = "Deluxe Voter Crate";

	private static final Pattern CHANCE = Pattern.compile(
			"chance:\\s*([0-9]+(?:\\.[0-9]+)?)\\s*%", Pattern.CASE_INSENSITIVE);

	private VoteOdds() {
	}

	/** Whether a menu title names a crate whose odds are worth keeping. */
	public static boolean isVoterCrate(String title) {
		if (title == null) {
			return false;
		}
		String trimmed = title.trim();
		return trimmed.equalsIgnoreCase(VOTER) || trimmed.equalsIgnoreCase(DELUXE_VOTER);
	}

	/**
	 * The percentage a lore line states, or null if it states none.
	 *
	 * @return e.g. 7.6 for {@code "┃ Chance: 7.6%"}
	 */
	public static Double parseChance(String line) {
		if (line == null) {
			return null;
		}
		Matcher matcher = CHANCE.matcher(line);
		if (!matcher.find()) {
			return null;
		}
		try {
			return Double.parseDouble(matcher.group(1));
		} catch (NumberFormatException malformed) {
			// A figure too long for a double is not a figure worth keeping.
			return null;
		}
	}

	/** The first chance any of a reward's lore lines states, or null. */
	public static Double chanceIn(List<String> lore) {
		if (lore == null) {
			return null;
		}
		for (String line : lore) {
			Double chance = parseChance(line);
			if (chance != null) {
				return chance;
			}
		}
		return null;
	}

	/**
	 * Replaces everything known about one crate with a fresh reading.
	 *
	 * <p>Authoritative rather than merged: the server is free to change a crate's table, and
	 * a reward that has left it should leave with it instead of lingering as a stale figure.
	 *
	 * @return the new list of entries across all crates
	 */
	public static List<VoteOddsEntry> relearn(List<VoteOddsEntry> existing, String crate,
			Map<String, Double> chances) {
		List<VoteOddsEntry> out = new ArrayList<>();
		if (existing != null) {
			for (VoteOddsEntry entry : existing) {
				if (entry != null && entry.crate != null && !entry.crate.equalsIgnoreCase(crate)) {
					out.add(entry);
				}
			}
		}
		if (chances != null) {
			chances.forEach((item, chance) -> {
				if (item != null && !item.isBlank() && chance != null && chance > 0d) {
					out.add(new VoteOddsEntry(crate, item, chance));
				}
			});
		}
		return out;
	}

	/**
	 * One crate's rewards and their chances.
	 *
	 * <p>A type rather than a bare map because the keys are normalised — the roll screen's name
	 * has to match the odds menu's whatever the casing — and a caller handing over a map it
	 * built itself would silently miss every lookup. {@link #of} is the only way in, so the
	 * normalisation cannot be forgotten.
	 */
	public record Table(Map<String, Double> byKey) {
		public static final Table EMPTY = new Table(Map.of());

		/** Normalises and keeps only rewards with a usable chance. */
		public static Table of(Map<String, Double> chances) {
			if (chances == null || chances.isEmpty()) {
				return EMPTY;
			}
			Map<String, Double> out = new LinkedHashMap<>();
			chances.forEach((item, chance) -> {
				if (item != null && !item.isBlank() && chance != null && chance > 0d) {
					out.put(key(item), chance);
				}
			});
			return out.isEmpty() ? EMPTY : new Table(out);
		}

		/** A reward's chance, or null when this crate's odds were never read. */
		public Double chance(String item) {
			return item == null ? null : byKey.get(key(item));
		}

		public boolean isEmpty() {
			return byKey.isEmpty();
		}

		public int size() {
			return byKey.size();
		}
	}

	/** One crate's table out of everything remembered. */
	public static Table tableFor(List<VoteOddsEntry> entries, String crate) {
		if (entries == null || crate == null) {
			return Table.EMPTY;
		}
		Map<String, Double> out = new LinkedHashMap<>();
		for (VoteOddsEntry entry : entries) {
			if (entry == null || entry.crate == null || entry.item == null) {
				continue;
			}
			if (entry.crate.equalsIgnoreCase(crate) && entry.chance > 0d) {
				out.put(entry.item, entry.chance);
			}
		}
		return Table.of(out);
	}

	/**
	 * Which of the drawn rewards is the rarest, or -1 when nothing is known about them or two
	 * are equally rare.
	 *
	 * <p>Deliberately "rarest" and not "best": a $75,000 at 3% and a Mystery Crate Key at 1%
	 * are not ranked by odds alone, and the mod has no business deciding which a player wants.
	 */
	public static int rarest(Table table, List<String> drawn) {
		if (table == null || drawn == null) {
			return -1;
		}
		int found = -1;
		double lowest = Double.MAX_VALUE;
		for (int i = 0; i < drawn.size(); i++) {
			Double chance = table.chance(drawn.get(i));
			if (chance == null) {
				continue;
			}
			if (chance < lowest) {
				lowest = chance;
				found = i;
			} else if (chance == lowest) {
				// Two draws of the same reward, or two equally rare ones: flagging one of them
				// would read as a recommendation the odds do not support.
				found = -1;
			}
		}
		return found;
	}

	/** "1 in 53" — how a percentage reads to somebody deciding what to click. */
	public static String oneIn(double chance) {
		if (chance <= 0d) {
			return "";
		}
		return "1 in " + Math.max(1, Math.round(100d / chance));
	}

	private static String key(String item) {
		return item.trim().toLowerCase(Locale.ROOT);
	}
}
