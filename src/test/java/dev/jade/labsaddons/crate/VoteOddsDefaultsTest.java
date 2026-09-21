package dev.jade.labsaddons.crate;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The odds that ship in the jar, against the menus they were read from.
 *
 * <p>Worth asserting because a player cannot correct this file: a wrong figure here is shown
 * confidently under a real draw, and the only thing that would ever put it right is punching
 * that crate. Both captured menus listed twenty-seven rewards summing to just under a hundred
 * percent, so anything else means the file was edited or generated wrong.
 */
class VoteOddsDefaultsTest {
	private static final double VOTER_TOTAL = 98.4d;
	private static final double DELUXE_TOTAL = 99.0d;

	@Test
	void bothCratesShipWithTheirFullTable() {
		Map<String, List<VoteOddsEntry>> bundled = VoteOddsDefaults.get();
		assertEquals(2, bundled.size(), "both voter crates, and nothing else");
		assertEquals(27, bundled.get("Voter Crate").size());
		assertEquals(27, bundled.get("Deluxe Voter Crate").size());
	}

	@Test
	void theChancesStillSumToWhatTheMenusStated() {
		assertEquals(VOTER_TOTAL, total("Voter Crate"), 0.05d);
		assertEquals(DELUXE_TOTAL, total("Deluxe Voter Crate"), 0.05d);
	}

	@Test
	void everyShippedRewardIsUsable() {
		for (List<VoteOddsEntry> crate : VoteOddsDefaults.get().values()) {
			for (VoteOddsEntry entry : crate) {
				assertNotNull(entry.item);
				assertTrue(!entry.item.isBlank(), "a reward with no name matches nothing");
				assertTrue(entry.chance > 0d && entry.chance <= 100d,
						entry.item + " has an impossible chance: " + entry.chance);
			}
		}
	}

	@Test
	void theDuplicateNameSurvivesIntoTheFileAndIsDeclinedByTheTable() {
		// The file must keep both Enhanced Farming Access rows — collapsing them there would
		// hide the ambiguity from the one thing that knows to decline it.
		List<VoteOddsEntry> voter = VoteOddsDefaults.get().get("Voter Crate");
		assertEquals(2, voter.stream()
				.filter(entry -> "Enhanced Farming Access".equals(entry.item)).count());
		assertNull(VoteOdds.Table.of(voter).chance("Enhanced Farming Access"));
		// Twenty-seven rewards under twenty-six distinct names, less the one name that is
		// shared: twenty-five of the crate's rewards can still be named a figure.
		assertEquals(25, VoteOdds.Table.of(voter).size());
	}

	@Test
	void aRealThreeWayDrawResolvesWithNothingScraped() {
		// The draw from the screenshot: no crate had been punched, and all three still read.
		VoteOdds.Table table = VoteOdds.Table.of(VoteOddsDefaults.get().get("Voter Crate"));
		List<String> drawn = List.of("Voter Armour Set", "Mount Rental Coupon (15m)",
				"Crate Scrap x3");
		assertEquals(2.8d, table.chance(drawn.get(0)));
		assertEquals(3.8d, table.chance(drawn.get(1)));
		assertEquals(5.7d, table.chance(drawn.get(2)));
		assertEquals(0, VoteOdds.rarest(table, drawn));
	}

	private static double total(String crate) {
		return VoteOddsDefaults.get().get(crate).stream()
				.mapToDouble(entry -> entry.chance).sum();
	}
}
