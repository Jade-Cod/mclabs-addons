package dev.jade.labsaddons.crate;

/**
 * One reward's published chance in one crate, as it is written to {@code state.json}.
 *
 * <p>A plain class with public fields rather than a record, matching every other persisted
 * shape in this config. Flat rather than a nested map per crate because a flat list survives
 * a hand-edited file without a null map to guard at every read.
 */
public class VoteOddsEntry {
	/** The crate's menu title, e.g. "Voter Crate". */
	public String crate = "";
	/** The reward's display name, which is the only thing the roll screen gives us to match on. */
	public String item = "";
	/** Percent, as the menu states it: 7.6 for "Chance: 7.6%". */
	public double chance;

	public VoteOddsEntry() {
	}

	public VoteOddsEntry(String crate, String item, double chance) {
		this.crate = crate == null ? "" : crate;
		this.item = item == null ? "" : item;
		this.chance = chance;
	}
}
