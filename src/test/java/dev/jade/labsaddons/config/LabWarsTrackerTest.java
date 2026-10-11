package dev.jade.labsaddons.config;

import dev.jade.labsaddons.labwars.LabWarsBooster;
import dev.jade.labsaddons.labwars.LabWarsTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lines are from scenarios labwars-boosts, labwars-active-listing and labwars-active-relisted.
 * Lives in the config package for {@code useStore}.
 */
class LabWarsTrackerTest {
	private static final long SLACK_MS = 5_000L;
	private static final long MIN = 60_000L;

	private static final String EVENT = "\nLab Wars » Revenue Boost Event: The revenue rate of Fishing has been boosted"
			+ " by 1.25x for the next 1 hour.\n";
	private static final String SHOP = "\nLab Wars » Shop Boost Activated: The revenue rate of Chem Sales has been"
			+ " boosted by 1.1x for the next 5 minutes by Zemperus.\n";
	private static final String EXTENDED = "Lab Wars » Shop Boost Extended: The 1.1x revenue rate of Chem Sales has been"
			+ " extended another 5 minutes by Zemperus.";
	private static final String LISTING = "\nLab Wars » Active Revenue Boosts:\n - The Pit: 1.5x\n - Chem Sales: 1.1x\n"
			+ "Click this message for more info.\n";
	private static final String RELISTED = "\nLab Wars » Active Revenue Boosts:\n - The Pit: 2.25x\n - Chem Sales: 1.1x\n"
			+ "Click this message for more info.\n";

	@TempDir
	Path configDir;

	@BeforeEach
	void freshConfig() {
		LabsAddonsConfig.useStore(new ConfigStore(configDir));
		LabWarsTracker.clear();
	}

	private static List<LabWarsBooster> raw() {
		return LabsAddonsConfig.get().labWarsActive;
	}

	private static LabWarsBooster only(String key) {
		List<LabWarsBooster> hits = raw().stream().filter(b -> b.key.equals(key)).toList();
		assertEquals(1, hits.size(), key + " rows: " + hits.size());
		return hits.get(0);
	}

	@Test
	void eventLineWithNoTrailingByIsTracked() {
		LabWarsTracker.onMessage(EVENT);
		LabWarsBooster b = only("fishing");
		assertEquals(1.25, b.multiplier, 1e-9);
		assertTrue(b.timerKnown);
		assertEquals(60 * MIN, b.remainingMs(), SLACK_MS);
	}

	@Test
	void shopLineNamesWhoBoughtItAndStillParses() {
		LabWarsTracker.onMessage(SHOP);
		LabWarsBooster b = only("chem_sales");
		assertEquals(1.1, b.multiplier, 1e-9);
		assertEquals(5 * MIN, b.remainingMs(), SLACK_MS);
	}

	@Test
	void compactDurationFormatsParse() {
		LabWarsTracker.onMessage("Lab Wars » Shop Boost Activated: The revenue rate of Voting has been boosted"
				+ " by 1.5x for the next 10m:50s by Zemperus.");
		assertEquals(10 * MIN + 50_000L, only("voting").remainingMs(), SLACK_MS);
		LabWarsTracker.onMessage("Lab Wars » Revenue Boost Event: The revenue rate of Arrests has been boosted"
				+ " by 2x for the next 02h:10m.");
		assertEquals(130 * MIN, only("arrests").remainingMs(), SLACK_MS);
	}

	@Test
	void unknownCategoryIsIgnored() {
		LabWarsTracker.onMessage("Lab Wars » Revenue Boost Event: The revenue rate of Mining has been boosted"
				+ " by 2x for the next 1 hour.");
		assertTrue(raw().isEmpty());
	}

	@Test
	void sameMultiplierReactivationRefreshesInsteadOfStacking() {
		LabWarsTracker.onMessage(SHOP);
		LabWarsTracker.onMessage(SHOP.replace("5 minutes", "20 minutes"));
		assertEquals(1, raw().size());
		assertEquals(20 * MIN, raw().get(0).remainingMs(), SLACK_MS);
	}

	@Test
	void differentMultipliersInOneCategoryKeepSeparateRows() {
		LabWarsTracker.onMessage(EVENT);
		LabWarsTracker.onMessage(EVENT.replace("1.25x", "1.1x").replace("1 hour", "5 minutes"));
		assertEquals(2, raw().size());
		assertEquals(5 * MIN, LabWarsTracker.active().get(0).remainingMs(), SLACK_MS);
	}

	@Test
	void extensionAddsToTheMatchingBoostersTimer() {
		LabWarsTracker.onMessage(SHOP);
		LabWarsTracker.onMessage(EXTENDED);
		assertEquals(1, raw().size());
		assertEquals(10 * MIN, raw().get(0).remainingMs(), SLACK_MS);
	}

	@Test
	void extensionWithoutAnotherStillParses() {
		LabWarsTracker.onMessage(SHOP);
		LabWarsTracker.onMessage(EXTENDED.replace("another ", ""));
		assertEquals(10 * MIN, raw().get(0).remainingMs(), SLACK_MS);
	}

	@Test
	void extensionOfABoostWeNeverSawLeavesATimerlessRow() {
		LabWarsTracker.onMessage(EXTENDED);
		LabWarsBooster b = only("chem_sales");
		assertFalse(b.timerKnown);
		assertEquals(0L, b.expiryEpochMs);
		assertEquals(1.1, b.multiplier, 1e-9);
	}

	@Test
	void extensionDoesNotTouchAPlaceholderTimer() {
		LabWarsTracker.onMessage(EXTENDED);
		LabWarsTracker.onMessage(EXTENDED);
		assertEquals(1, raw().size());
		assertEquals(0L, raw().get(0).expiryEpochMs);
	}

	@Test
	void summaryAddsPlaceholdersWithTheCombinedMultiplier() {
		LabWarsTracker.onMessage(LISTING);
		assertEquals(2, raw().size());
		LabWarsBooster pit = only("pit");
		assertEquals(1.5, pit.multiplier, 1e-9);
		assertFalse(pit.timerKnown);
		assertEquals(1.1, only("chem_sales").multiplier, 1e-9);
	}

	@Test
	void laterSummaryReplacesAPlaceholdersMultiplier() {
		LabWarsTracker.onMessage(LISTING);
		LabWarsTracker.onMessage(RELISTED);
		assertEquals(2, raw().size());
		assertEquals(2.25, only("pit").multiplier, 1e-9);
	}

	@Test
	void summaryLeavesABoostWithAKnownTimerAlone() {
		LabWarsTracker.onMessage(SHOP);
		LabWarsTracker.onMessage(LISTING);
		LabWarsBooster chem = only("chem_sales");
		assertTrue(chem.timerKnown);
		assertEquals(5 * MIN, chem.remainingMs(), SLACK_MS);
		assertEquals(1.5, only("pit").multiplier, 1e-9);
	}

	@Test
	void summaryDropsPlaceholdersForCategoriesItNoLongerLists() {
		LabWarsTracker.onMessage(LISTING);
		LabWarsTracker.onMessage("\nLab Wars » Active Revenue Boosts:\n - Chem Sales: 1.1x\nClick this message for more info.\n");
		assertEquals(1, raw().size());
		assertEquals("chem_sales", raw().get(0).key);
	}

	@Test
	void summaryKeepsTimedBoostsOfUnlistedCategories() {
		LabWarsTracker.onMessage(EVENT);
		LabWarsTracker.onMessage("\nLab Wars » Active Revenue Boosts:\n - Chem Sales: 1.1x\nClick this message for more info.\n");
		assertEquals(2, raw().size());
		assertTrue(only("fishing").timerKnown);
	}

	@Test
	void expiredBoostsArePrunedAndSavedAway() {
		LabWarsTracker.setCategory("fishing", List.of(
				new LabWarsBooster("fishing", 1.25, System.currentTimeMillis() - 1_000L, true),
				new LabWarsBooster("fishing", 1.1, System.currentTimeMillis() + 10 * MIN, true)));
		List<LabWarsBooster> live = LabWarsTracker.active();
		assertEquals(1, live.size());
		assertEquals(1.1, live.get(0).multiplier, 1e-9);
		assertEquals(1, raw().size());
	}

	@Test
	void placeholdersNeverExpire() {
		LabWarsTracker.onMessage(LISTING);
		assertEquals(2, LabWarsTracker.active().size());
	}

	@Test
	void activeListsTimedBoostsSoonestFirstThenPlaceholders() {
		LabWarsTracker.onMessage(LISTING);
		LabWarsTracker.onMessage(EVENT);
		LabWarsTracker.onMessage(SHOP);
		List<LabWarsBooster> live = LabWarsTracker.active();
		assertEquals(List.of("chem_sales", "fishing"), live.subList(0, 2).stream().map(b -> b.key).toList());
		assertEquals(3, live.size());
		assertEquals("pit", live.get(2).key);
		assertFalse(live.get(2).timerKnown);
	}

	@Test
	void timedActivationUpgradesAnEqualMultiplierPlaceholder() {
		LabWarsTracker.onMessage(LISTING);
		LabWarsTracker.onMessage(SHOP);
		assertEquals(2, raw().size());
		assertTrue(only("chem_sales").timerKnown);
	}

	@Test
	void setCategoryReplacesOnlyThatCategory() {
		LabWarsTracker.onMessage(EVENT);
		LabWarsTracker.onMessage(SHOP);
		LabWarsTracker.setCategory("fishing", List.of());
		assertEquals(1, raw().size());
		assertEquals("chem_sales", raw().get(0).key);
	}

	@Test
	void clearEmptiesTheList() {
		LabWarsTracker.onMessage(EVENT);
		LabWarsTracker.clear();
		assertTrue(raw().isEmpty());
	}

	@Test
	void unrelatedLinesMoveNothing() {
		LabWarsTracker.onMessage("Lab Wars » Your lab has earned $1.2m this hour.");
		LabWarsTracker.onMessage("MCLabs » Vote registered! Claim your vote rewards with /claimvotes");
		assertTrue(raw().isEmpty());
	}
}
