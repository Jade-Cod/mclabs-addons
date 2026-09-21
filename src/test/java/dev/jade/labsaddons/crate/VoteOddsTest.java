package dev.jade.labsaddons.crate;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
	/** The two Voter Crate variants, verbatim. */
	private static final List<String> NINETY =
			List.of("Info: /efarm", "", "Duration: 90 minutes");
	private static final List<String> SIXTY =
			List.of("Info: /efarm", "", "Duration: 60 minutes");

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
		assertEquals(0, VoteOdds.rarest(table, drawn("mcMMO Credit x20", "$3,000", "$3,000")));
	}

	@Test
	void twoEquallyRareDrawsFlagNeither() {
		// Flagging one of a tie would read as a recommendation the odds cannot support.
		VoteOdds.Table table = VoteOdds.Table.of(table("$3,000", 7.6d, "Insta-Grow x2", 7.6d));
		assertEquals(-1, VoteOdds.rarest(table, drawn("$3,000", "Insta-Grow x2")));
	}

	@Test
	void aTieAboveSomethingRarerStillFlagsTheRareOne() {
		VoteOdds.Table table =
				VoteOdds.Table.of(table("$3,000", 7.6d, "mcMMO Credit x20", 1.9d));
		assertEquals(2, VoteOdds.rarest(table, drawn("$3,000", "$3,000", "mcMMO Credit x20")));
	}

	@Test
	void nothingIsFlaggedWhenTheCratesOddsWereNeverRead() {
		assertEquals(-1, VoteOdds.rarest(VoteOdds.Table.EMPTY, drawn("$3,000", "Hopper x2", "EXP x150")));
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
		List<VoteOddsEntry> chances = new ArrayList<>(table("Good", 1d, "Zero", 0d, "", 5d));
		chances.add(null);
		List<VoteOddsEntry> known = VoteOdds.relearn(List.of(), "Voter Crate", chances);
		assertEquals(1, known.size());
		assertEquals("Good", known.get(0).item);
	}

	@Test
	void aNameTwoRewardsShareIsToldApartByItsLore() {
		// Verbatim from the Voter Crate: Enhanced Farming Access is listed twice, ninety
		// minutes at 0.9% and sixty at 2.8%, and the roll screen calls both the same thing.
		// The duration is the only thing separating them.
		List<VoteOddsEntry> entries = List.of(
				new VoteOddsEntry("Voter Crate", "Enhanced Farming Access", 0.9d, NINETY),
				new VoteOddsEntry("Voter Crate", "Enhanced Farming Access", 2.8d, SIXTY),
				new VoteOddsEntry("Voter Crate", "Voter Shovel", 2.8d, List.of()));
		VoteOdds.Table table = VoteOdds.Table.of(entries);

		assertEquals(0.9d, table.chance("Enhanced Farming Access", NINETY));
		assertEquals(2.8d, table.chance("Enhanced Farming Access", SIXTY));
		assertEquals(3, table.size());
	}

	@Test
	void aSharedNameWithNoLoreToMatchOnIsDeclinedRatherThanGuessed() {
		// Better no figure than whichever of the two happened to be stored first.
		List<VoteOddsEntry> entries = List.of(
				new VoteOddsEntry("Voter Crate", "Enhanced Farming Access", 0.9d, NINETY),
				new VoteOddsEntry("Voter Crate", "Enhanced Farming Access", 2.8d, SIXTY));
		VoteOdds.Table table = VoteOdds.Table.of(entries);
		assertNull(table.chance("Enhanced Farming Access"));
		assertNull(table.chance("Enhanced Farming Access", List.of("Duration: 5 minutes")));
	}

	@Test
	void aUniqueNameIsMatchedWithoutItsLore() {
		// So a table keeps working if the server retunes an enchantment on a reward.
		VoteOdds.Table table = VoteOdds.Table.of(List.of(
				new VoteOddsEntry("Voter Crate", "Voter Shovel", 2.8d,
						List.of("Efficiency IV", "Unbreaking II"))));
		assertEquals(2.8d, table.chance("Voter Shovel", List.of("Efficiency V")));
		assertEquals(2.8d, table.chance("Voter Shovel"));
	}

	@Test
	void theScreensOwnAnnotationsAreNotPartOfARewardsLore() {
		// The odds menu appends its chance, the choice appends "Choice 2/3"; strip both and
		// what is left matches exactly across the two.
		assertEquals(List.of("Info: /efarm", "", "Duration: 90 minutes"),
				VoteOdds.intrinsicLore(List.of("Info: /efarm", "", "Duration: 90 minutes",
						"┃", "┃ Chance: 0.9%")));
		assertEquals(List.of(), VoteOdds.intrinsicLore(
				List.of("┃", "┃ Choice 2/3", "┃ Click to claim this item!")));
		assertEquals(List.of(), VoteOdds.intrinsicLore(null));
	}

	@Test
	void aTimedRewardSaysHowLongItLasts() {
		// The whole point: three rewards across the two crates are called Enhanced Farming
		// Access and differ only in this.
		assertEquals("90 minutes", VoteOdds.duration(NINETY));
		assertEquals("60 minutes", VoteOdds.duration(SIXTY));
		assertEquals("120 minutes",
				VoteOdds.duration(List.of("Info: /efarm", "", "Duration: 120 minutes")));
		assertEquals("", VoteOdds.duration(List.of("Efficiency IV", "Unbreaking II")));
		assertEquals("", VoteOdds.duration(null));
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

	/** One crate's entries, in the shape both the jar and a scrape produce. */
	private static List<VoteOddsEntry> table(Object... pairs) {
		List<VoteOddsEntry> out = new ArrayList<>();
		for (int i = 0; i < pairs.length; i += 2) {
			out.add(new VoteOddsEntry("Voter Crate", (String) pairs[i], (Double) pairs[i + 1]));
		}
		return out;
	}

	private static List<VoteOdds.Draw> drawn(String... names) {
		List<VoteOdds.Draw> out = new ArrayList<>();
		for (String name : names) {
			out.add(new VoteOdds.Draw(name, List.of()));
		}
		return out;
	}
}
