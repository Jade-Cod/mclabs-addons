package dev.jade.labsaddons.crate;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The voter crate's odds, against the lore the server actually writes.
 *
 * <p>The three-way choice is made blind without this: the roll screen states no odds at all, so
 * every figure the board shows comes from a table scraped earlier and matched by name.
 */
class VoteOddsTest {
	@Test
	void theChanceIsReadOffTheServersOwnLoreLine() {
		// Verbatim from votecrate-odds.jsonl, box-drawing prefix included.
		assertEquals(7.6d, VoteOdds.parseChance("┃ Chance: 7.6%"));
		assertEquals(0.9d, VoteOdds.parseChance("┃ Chance: 0.9%"));
		// The deluxe crate writes whole numbers without a decimal point.
		assertEquals(1d, VoteOdds.parseChance("┃ Chance: 1%"));
	}

	@Test
	void aLineStatingNoChanceReadsAsNone() {
		assertNull(VoteOdds.parseChance("Invisibility (12:00)"));
		assertNull(VoteOdds.parseChance("┃"));
		assertNull(VoteOdds.parseChance(null));
	}

	@Test
	void theChanceIsFoundWhereverItSitsInTheLore() {
		// The Potion of Invisibility carries two lines before its chance.
		assertEquals(7.6d, VoteOdds.chanceIn(
				List.of("Invisibility (12:00)", "┃", "┃ Chance: 7.6%")));
		assertNull(VoteOdds.chanceIn(List.of("Info: /efarm", "Duration: 90 minutes")));
		assertNull(VoteOdds.chanceIn(null));
	}

	@Test
	void theRarestOfThreeIsTheLowestChance() {
		// The real draw captured in votecrate-roll.jsonl: one 1.9% beside two 7.6%.
		VoteOdds.Table table = VoteOdds.Table.of(table(
				"mcMMO Credit x20", 1.9d,
				"$3,000", 7.6d));
		assertEquals(0, VoteOdds.rarest(table,
				List.of("mcMMO Credit x20", "$3,000", "$3,000")));
	}

	@Test
	void twoEquallyRareDrawsFlagNeither() {
		// Flagging one of a tie would read as a recommendation the odds cannot support.
		VoteOdds.Table table = VoteOdds.Table.of(table("$3,000", 7.6d, "Insta-Grow x2", 7.6d));
		assertEquals(-1, VoteOdds.rarest(table, List.of("$3,000", "Insta-Grow x2")));
	}

	@Test
	void aTieAboveSomethingRarerStillFlagsTheRareOne() {
		VoteOdds.Table table =
				VoteOdds.Table.of(table("$3,000", 7.6d, "mcMMO Credit x20", 1.9d));
		assertEquals(2, VoteOdds.rarest(table,
				List.of("$3,000", "$3,000", "mcMMO Credit x20")));
	}

	@Test
	void nothingIsFlaggedWhenTheCratesOddsWereNeverRead() {
		assertEquals(-1, VoteOdds.rarest(VoteOdds.Table.EMPTY,
				List.of("$3,000", "Hopper x2", "EXP x150")));
		assertEquals(-1, VoteOdds.rarest(null, null));
	}

	@Test
	void aTableNormalisesWhateverItIsBuiltFrom() {
		// The footgun this type exists to close: a raw map of original-case names used to miss
		// every lookup, because the reader lowercased its keys and callers did not.
		VoteOdds.Table table = VoteOdds.Table.of(table("mcMMO Credit x20", 1.9d));
		assertEquals(1.9d, table.chance("MCMMO CREDIT X20"));
		assertTrue(VoteOdds.Table.of(null).isEmpty());
		assertTrue(VoteOdds.Table.of(table("Zero", 0d)).isEmpty());
	}

	@Test
	void relearningACrateReplacesItAndLeavesTheOtherAlone() {
		List<VoteOddsEntry> known = VoteOdds.relearn(List.of(), "Voter Crate",
				table("$3,000", 7.6d, "Hopper x2", 3.8d));
		known = VoteOdds.relearn(known, "Deluxe Voter Crate", table("$75,000", 3d));
		// A reward that has left the table should leave with it, not linger as a stale figure.
		known = VoteOdds.relearn(known, "Voter Crate", table("$3,000", 9d));

		VoteOdds.Table voter = VoteOdds.tableFor(known, "Voter Crate");
		assertEquals(1, voter.size());
		assertEquals(9d, voter.chance("$3,000"));
		assertNull(voter.chance("Hopper x2"));
		assertEquals(3d, VoteOdds.tableFor(known, "Deluxe Voter Crate").chance("$75,000"));
	}

	@Test
	void aRewardIsMatchedRegardlessOfCaseOrPadding() {
		List<VoteOddsEntry> known = VoteOdds.relearn(List.of(), "Voter Crate",
				table("Potion of Invisibility", 7.6d));
		assertEquals(7.6d, VoteOdds.tableFor(known, "voter crate")
				.chance("  potion of INVISIBILITY "));
	}

	@Test
	void anEntryWithoutAUsableChanceIsNotKept() {
		Map<String, Double> chances = new LinkedHashMap<>();
		chances.put("Good", 1d);
		chances.put("Zero", 0d);
		chances.put("", 5d);
		chances.put("Null", null);
		List<VoteOddsEntry> known = VoteOdds.relearn(List.of(), "Voter Crate", chances);
		assertEquals(1, known.size());
		assertEquals("Good", known.get(0).item);
	}

	@Test
	void onlyTheTwoVoterCratesAreScraped() {
		assertTrue(VoteOdds.isVoterCrate("Voter Crate"));
		assertTrue(VoteOdds.isVoterCrate("Deluxe Voter Crate"));
		// A supply crate's reward menu carries rarities, not chances, and is paginated.
		assertFalse(VoteOdds.isVoterCrate("Supply Crate II"));
		assertFalse(VoteOdds.isVoterCrate("Your Exceedingly Rare odds"));
		assertFalse(VoteOdds.isVoterCrate(null));
	}

	@Test
	void aChanceReadsAsOddsSomebodyDecidingCanUse() {
		assertEquals("1 in 53", VoteOdds.oneIn(1.9d));
		assertEquals("1 in 13", VoteOdds.oneIn(7.6d));
		assertEquals("1 in 100", VoteOdds.oneIn(1d));
		assertEquals("", VoteOdds.oneIn(0d));
	}

	private static Map<String, Double> table(Object... pairs) {
		Map<String, Double> out = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			out.put((String) pairs[i], (Double) pairs[i + 1]);
		}
		return out;
	}
}
