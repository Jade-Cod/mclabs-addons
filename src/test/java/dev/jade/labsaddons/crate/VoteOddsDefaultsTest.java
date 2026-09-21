package dev.jade.labsaddons.crate;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
	void bothEnhancedFarmingAccessesShipWithTheDurationThatTellsThemApart() {
		List<VoteOddsEntry> voter = VoteOddsDefaults.get().get("Voter Crate");
		assertEquals(2, voter.stream()
				.filter(entry -> "Enhanced Farming Access".equals(entry.item)).count());

		VoteOdds.Table table = VoteOdds.Table.of(voter);
		assertEquals(0.9d, table.chance("Enhanced Farming Access",
				List.of("Info: /efarm", "", "Duration: 90 minutes")));
		assertEquals(2.8d, table.chance("Enhanced Farming Access",
				List.of("Info: /efarm", "", "Duration: 60 minutes")));
		// All twenty-seven are reachable now that the lore separates the pair.
		assertEquals(27, table.size());
	}

	@Test
	void theDeluxeCrateShipsItsOwnHundredAndTwentyMinuteVersion() {
		// Its name is unique in that crate, so nothing forces the duration out — which is
		// exactly why the board shows it regardless of whether the name is ambiguous.
		VoteOddsEntry deluxe = VoteOddsDefaults.get().get("Deluxe Voter Crate").stream()
				.filter(entry -> "Enhanced Farming Access".equals(entry.item))
				.findFirst().orElseThrow();
		assertEquals("120 minutes", VoteOdds.duration(deluxe.lore()));
		assertEquals(4d, deluxe.chance);
	}

	@Test
	void aRealThreeWayDrawResolvesWithNothingScraped() {
		// The draw from the screenshot: no crate had been punched, and all three still read.
		VoteOdds.Table table = VoteOdds.Table.of(VoteOddsDefaults.get().get("Voter Crate"));
		List<VoteOdds.Draw> drawn = List.of(
				new VoteOdds.Draw("Voter Armour Set", List.of("Protection III", "Unbreaking II")),
				new VoteOdds.Draw("Mount Rental Coupon (15m)", List.of()),
				new VoteOdds.Draw("Crate Scrap x3", List.of()));
		assertEquals(2.8d, table.chance(drawn.get(0).item(), drawn.get(0).lore()));
		assertEquals(3.8d, table.chance(drawn.get(1).item(), drawn.get(1).lore()));
		assertEquals(5.7d, table.chance(drawn.get(2).item(), drawn.get(2).lore()));
		assertEquals(0, VoteOdds.rarest(table, drawn));
	}

	@Test
	void theFiftyThousandKeepsTheServersOwnSpacedSpelling() {
		// Every other cash reward is written with a comma. This one is "$50 000", with a space,
		// in the odds menu and on the roll screen alike — measured byte for byte in both dumps.
		// Tidying it to "$50,000" would make the lookup miss, and the reward would show "odds
		// unknown" on the one screen where the figure decides what a player clicks.
		VoteOdds.Table table =
				VoteOdds.Table.of(VoteOddsDefaults.get().get("Deluxe Voter Crate"));
		assertEquals(5.0d, table.chance("$50 000"));
	}

	private static double total(String crate) {
		return VoteOddsDefaults.get().get(crate).stream()
				.mapToDouble(entry -> entry.chance).sum();
	}
}
