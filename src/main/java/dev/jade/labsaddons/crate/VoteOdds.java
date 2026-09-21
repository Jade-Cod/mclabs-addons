package dev.jade.labsaddons.crate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
	private static final Pattern DURATION = Pattern.compile(
			"duration:\\s*(.+)", Pattern.CASE_INSENSITIVE);

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
	 * One crate's rewards, looked up by what the roll screen shows.
	 *
	 * <p>A name alone identifies almost every reward, and is matched on its own wherever it is
	 * unique — so a table keeps working if the server retunes an enchantment. Where a crate
	 * lists two rewards under one name, the lore decides: the Voter Crate has Enhanced Farming
	 * Access twice, ninety minutes at 0.9% and sixty at 2.8%, and without that they cannot be
	 * told apart at all.
	 */
	public static final class Table {
		public static final Table EMPTY = new Table(Map.of(), Map.of());

		/** Names no other reward in this crate shares. */
		private final Map<String, Double> unique;
		/** Names two or more rewards share, each keeping every candidate. */
		private final Map<String, List<VoteOddsEntry>> shared;

		private Table(Map<String, Double> unique, Map<String, List<VoteOddsEntry>> shared) {
			this.unique = unique;
			this.shared = shared;
		}

		/** Builds from one crate's entries. Duplicates must survive to here to be noticed. */
		public static Table of(List<VoteOddsEntry> entries) {
			if (entries == null || entries.isEmpty()) {
				return EMPTY;
			}
			Map<String, List<VoteOddsEntry>> byName = new LinkedHashMap<>();
			for (VoteOddsEntry entry : entries) {
				if (entry == null || entry.item == null || entry.item.isBlank()
						|| entry.chance <= 0d) {
					continue;
				}
				byName.computeIfAbsent(key(entry.item), any -> new ArrayList<>()).add(entry);
			}
			Map<String, Double> unique = new LinkedHashMap<>();
			Map<String, List<VoteOddsEntry>> shared = new LinkedHashMap<>();
			byName.forEach((name, group) -> {
				if (group.size() == 1) {
					unique.put(name, group.get(0).chance);
				} else {
					shared.put(name, List.copyOf(group));
				}
			});
			return unique.isEmpty() && shared.isEmpty()
					? EMPTY
					: new Table(Map.copyOf(unique), Map.copyOf(shared));
		}

		/**
		 * A reward's chance, or null when it is unknown.
		 *
		 * @param lore the item's own lore, with the screen's {@code ┃} annotations stripped
		 */
		public Double chance(String item, List<String> lore) {
			if (item == null) {
				return null;
			}
			String name = key(item);
			Double plain = unique.get(name);
			if (plain != null) {
				return plain;
			}
			VoteOddsEntry match = match(name, lore);
			return match == null ? null : match.chance;
		}

		/** The same, for a reward whose lore is not to hand. */
		public Double chance(String item) {
			return chance(item, List.of());
		}

		/** Whichever of a shared name's rewards has this lore, or null if none does. */
		private VoteOddsEntry match(String name, List<String> lore) {
			List<VoteOddsEntry> group = shared.get(name);
			if (group == null || lore == null) {
				return null;
            }
			for (VoteOddsEntry entry : group) {
				if (entry.lore().equals(lore)) {
					return entry;
				}
			}
			return null;
		}

		public boolean isEmpty() {
			return unique.isEmpty() && shared.isEmpty();
		}

		/** How many rewards can be named a figure. */
		public int size() {
			return unique.size() + shared.values().stream().mapToInt(List::size).sum();
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
					out.add(new VoteOddsEntry(crate, entry.item, entry.chance, entry.lore()));
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
	 * Whether every one of the draws has a figure in this table.
	 *
	 * <p>What {@link #rarest} rests on, and the difference between "these are equally rare" and
	 * "the odds are not in hand" — which look identical from the outside and mean the opposite.
	 */
	public static boolean allKnown(Table table, List<Draw> drawn) {
		if (table == null || drawn == null || drawn.isEmpty()) {
			return false;
		}
		for (Draw draw : drawn) {
			if (table.chance(draw.item(), draw.lore()) == null) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Which of the drawn rewards is the rarest, or -1 when nothing is known about them or two
	 * are equally rare.
	 *
	 * <p>Deliberately "rarest" and not "best": a $75,000 at 3% and a Mystery Crate Key at 1%
	 * are not ranked by odds alone, and the mod has no business deciding which a player wants.
	 *
	 * <p>And only when <em>all</em> of them are known. An unknown draw used to be skipped, so a
	 * 5% could be flagged the rarest of the three while the draw beside it was a 0.5% the table
	 * had no figure for — the mod pointing confidently at the wrong one, which is worse than it
	 * pointing at nothing.
	 */
	public static int rarest(Table table, List<Draw> drawn) {
		if (!allKnown(table, drawn)) {
			return -1;
		}
		int found = -1;
		double lowest = Double.MAX_VALUE;
		for (int i = 0; i < drawn.size(); i++) {
			Double chance = table.chance(drawn.get(i).item(), drawn.get(i).lore());
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

	/** One of the three rewards on the choice screen, as the container shows it. */
	public record Draw(String item, List<String> lore) {
		public Draw {
			item = item == null ? "" : item;
			lore = lore == null ? List.of() : List.copyOf(lore);
		}
	}

	/**
	 * A reward's own lore: the lines the screen did not add itself.
	 *
	 * <p>Both the odds menu and the choice append a {@code ┃} block — a chance on one, a
	 * "Choice 2/3" on the other — and dropping those leaves lore that matches exactly across
	 * the two, measured over every reward that appeared in both captures.
	 */
	public static List<String> intrinsicLore(List<String> lore) {
		if (lore == null) {
			return List.of();
		}
		List<String> out = new ArrayList<>(lore.size());
		for (String line : lore) {
			if (line != null && !line.trim().startsWith("┃")) {
				out.add(line);
			}
		}
		return out;
	}

	/**
	 * How long a timed reward lasts, as the server states it — "90 minutes" out of
	 * {@code "Duration: 90 minutes"} — or "" for a reward with no duration.
	 *
	 * <p>Worth pulling out on its own because it is the one thing a voter reward's name can
	 * leave out that changes what it is worth: Enhanced Farming Access comes in sixty, ninety
	 * and a hundred and twenty minute versions, all called the same thing.
	 */
	public static String duration(List<String> lore) {
		if (lore == null) {
			return "";
		}
		for (String line : lore) {
			if (line == null) {
				continue;
			}
			Matcher matcher = DURATION.matcher(line);
			if (matcher.find()) {
				return matcher.group(1).trim();
			}
		}
		return "";
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
