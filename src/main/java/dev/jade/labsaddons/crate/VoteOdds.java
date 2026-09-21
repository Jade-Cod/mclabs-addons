package dev.jade.labsaddons.crate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a voter crate's rewards are worth.
 *
 * <p>A voter crate has no rarity tiers at all — no coloured panes, no "Rarity:" lore, nothing
 * the roll screen can be read for. The only statement of how rare anything is lives in the
 * menu you get from punching the crate, as {@code "┃ Chance: 7.6%"} on each of its
 * twenty-seven rewards. Both tables ship with the mod, and punching a crate re-reads whichever
 * one you opened, so a table the server changes corrects itself without an update.
 *
 * <p>Pure and Minecraft-free: callers hand it names and lore lines they have already
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

	/** Whether a menu title names a crate whose odds are worth reading. */
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
	 * One crate's rewards, looked up by the name the roll screen shows.
	 *
	 * <p>A type rather than a bare map because two things have to hold and neither is obvious.
	 * Keys are normalised, so a name matches whatever its casing. And a display name two
	 * different rewards share is <b>dropped</b> rather than resolved: the Voter Crate lists
	 * Enhanced Farming Access twice, ninety minutes at 0.9% and sixty at 2.8%, and the roll
	 * screen calls both of them the same thing. Keeping one silently put a confident wrong
	 * figure under a real draw; showing none says what is actually known.
	 */
	public record Table(Map<String, Double> byKey) {
		public static final Table EMPTY = new Table(Map.of());

		/** Builds from one crate's entries. Duplicates must survive to here to be noticed. */
		public static Table of(List<VoteOddsEntry> entries) {
			if (entries == null || entries.isEmpty()) {
				return EMPTY;
			}
			Map<String, Double> out = new LinkedHashMap<>();
			Set<String> shared = new HashSet<>();
			for (VoteOddsEntry entry : entries) {
				if (entry == null || entry.item == null || entry.item.isBlank()
						|| entry.chance <= 0d) {
					continue;
				}
				String key = key(entry.item);
				Double seen = out.putIfAbsent(key, entry.chance);
				// Two rewards of the same name at the same chance are not ambiguous: whichever
				// one was drawn, the figure is that figure.
				if (seen != null && seen != entry.chance) {
					shared.add(key);
				}
			}
			out.keySet().removeAll(shared);
			return out.isEmpty() ? EMPTY : new Table(Map.copyOf(out));
		}

		/** A reward's chance, or null when it is unknown or its name is ambiguous. */
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

	/**
	 * Replaces everything known about one crate with a fresh reading.
	 *
	 * <p>Authoritative rather than merged: the server is free to change a crate's table, and a
	 * reward that has left it should leave with it instead of lingering as a stale figure.
	 */
	public static List<VoteOddsEntry> relearn(List<VoteOddsEntry> existing, String crate,
			List<VoteOddsEntry> fresh) {
		List<VoteOddsEntry> out = new ArrayList<>();
		if (existing != null) {
			for (VoteOddsEntry entry : existing) {
				if (entry != null && entry.crate != null && !entry.crate.equalsIgnoreCase(crate)) {
					out.add(entry);
				}
			}
		}
		if (fresh != null) {
			for (VoteOddsEntry entry : fresh) {
				if (entry != null && entry.item != null && !entry.item.isBlank()
						&& entry.chance > 0d) {
					out.add(new VoteOddsEntry(crate, entry.item, entry.chance));
				}
			}
		}
		return out;
	}

	/** One crate's entries out of everything remembered. */
	public static List<VoteOddsEntry> entriesFor(List<VoteOddsEntry> entries, String crate) {
		List<VoteOddsEntry> out = new ArrayList<>();
		if (entries == null || crate == null) {
			return out;
		}
		for (VoteOddsEntry entry : entries) {
			if (entry != null && entry.crate != null && entry.crate.equalsIgnoreCase(crate)) {
				out.add(entry);
			}
		}
		return out;
	}

	/** One crate's table out of everything remembered. */
	public static Table tableFor(List<VoteOddsEntry> entries, String crate) {
		return Table.of(entriesFor(entries, crate));
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
