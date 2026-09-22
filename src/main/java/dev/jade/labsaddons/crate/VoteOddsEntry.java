package dev.jade.labsaddons.crate;

import java.util.ArrayList;
import java.util.List;

/**
 * One reward's published chance in one crate, as it is written to {@code state.json} and
 * shipped in the jar.
 *
 * <p>A plain class with public fields rather than a record, matching every other persisted
 * shape in this config.
 */
public class VoteOddsEntry {
	/** The crate's menu title, e.g. "Voter Crate". */
	public String crate = "";
	/** The reward's display name, which is most of what identifies it. */
	public String item = "";
	/** Percent, as the menu states it: 7.6 for "Chance: 7.6%". */
	public double chance;
	/**
	 * The reward's own lore, with the screen's annotations removed.
	 *
	 * <p>Both menus append a {@code ┃} block of their own — the odds menu its chance, the
	 * choice its "Choice 2/3" — and stripping those leaves lore that matches exactly between
	 * the two. That is what lets a display name two rewards share still be told apart, and it
	 * is where the duration of a timed reward lives.
	 */
	public List<String> lore = new ArrayList<>();

	public VoteOddsEntry() {
	}

	public VoteOddsEntry(String crate, String item, double chance) {
		this(crate, item, chance, List.of());
	}

	public VoteOddsEntry(String crate, String item, double chance, List<String> lore) {
		this.crate = crate == null ? "" : crate;
		this.item = item == null ? "" : item;
		this.chance = chance;
		this.lore = lore == null ? new ArrayList<>() : new ArrayList<>(lore);
	}

	/** Never null, however the file was written. */
	public List<String> lore() {
		return lore == null ? List.of() : lore;
	}
}
