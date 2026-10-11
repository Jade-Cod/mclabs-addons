package dev.jade.labsaddons.crate;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Lines from the crate-vote-roll, crate-deluxe-vote-roll and daily-spin-roll captures. */
class RollsTakenTest {
	@Test
	void readsTheCrateAndRewardFromTheServersLine() {
		assertEquals(new RollsTaken.Taken("Voter Crate", "20 mcMMO Credits"),
				RollsTaken.parse("MCLabs » You opened a Voter Crate and unboxed 20 mcMMO Credits!"));
		assertEquals(new RollsTaken.Taken("Deluxe Voter Crate", "35 mcMMO Credits"),
				RollsTaken.parse("MCLabs » You opened a Deluxe Voter Crate and unboxed 35 mcMMO Credits!"));
		assertEquals(new RollsTaken.Taken(RollsTaken.DAILY, "High Roller Cash Roll"),
				RollsTaken.parse("MCLabs » You rolled your Daily Spin and chose High Roller Cash Roll!"));
		assertEquals(new RollsTaken.Taken(RollsTaken.DAILY, "Tag Credit x2"),
				RollsTaken.parse("\nMCLabs » You rolled your Daily Spin and chose Tag Credit x2!\n"));
	}

	@Test
	void otherCratesAndQuotedLinesAreNotTaken() {
		// A supply crate unboxes on the same line; the roll board only ever shows the voter crates.
		assertNull(RollsTaken.parse("MCLabs » You opened a Supply Crate I and unboxed Iron Pickaxe!"));
		assertNull(RollsTaken.parse("[S] [VIP] bob: MCLabs » You opened a Voter Crate and unboxed 20 mcMMO Credits!"));
		assertNull(RollsTaken.parse("Daily Spin » Daily bonus streak increased to 99 days! Spin again tomorrow to keep the streak alive."));
	}

	@Test
	void countingAddsOneAndLeavesTheOldTallyAlone() {
		Map<String, Map<String, Integer>> before = Map.of("Voter Crate", Map.of("$3,000", 2));
		Map<String, Map<String, Integer>> after = RollsTaken.counted(before,
				new RollsTaken.Taken("Voter Crate", "$3,000"));
		after = RollsTaken.counted(after, new RollsTaken.Taken(RollsTaken.DAILY, "Tag Credit x2"));

		assertEquals(3, after.get("Voter Crate").get("$3,000"));
		assertEquals(1, after.get(RollsTaken.DAILY).get("Tag Credit x2"));
		assertEquals(2, before.get("Voter Crate").get("$3,000"));
	}

	@Test
	void mostTakenComesFirstAndTiesGoByName() {
		List<Map.Entry<String, Integer>> order = RollsTaken.mostTaken(
				Map.of("Hopper x3", 1, "$75,000", 4, "20 mcMMO Credits", 1));
		assertEquals(List.of("$75,000", "20 mcMMO Credits", "Hopper x3"),
				order.stream().map(Map.Entry::getKey).toList());
	}
}
