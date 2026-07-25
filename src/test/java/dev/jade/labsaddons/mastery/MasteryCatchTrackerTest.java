package dev.jade.labsaddons.mastery;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Counts are inventory totals per challenge, as {@code MasteryCatchTracker.count} builds them. */
public class MasteryCatchTrackerTest {
	private static final String CATCH_COD = "Catch Cod";
	private static final String CATCH_FISH = "Catch Tropicalfish";
	private static final boolean FISHING = true;
	private static final boolean IDLE = false;

	@AfterEach
	public void reset() {
		MasteryTracker.clear();
		MasteryGains.clear();
		MasteryCatchTracker.reset();
	}

	private static void active(String... names) {
		MasteryTracker.setQuests(Arrays.stream(names)
				.map(n -> new MasteryQuest(null, n, 0, 100, 0))
				.toList());
	}

	private static double currentOf(String name) {
		return MasteryTracker.quests().stream()
				.filter(q -> q.name().equals(name))
				.findFirst().orElseThrow().current();
	}

	@Test
	public void activeCatchTargetsNormalisesAndIgnoresNonCatchQuests() {
		active(CATCH_COD, CATCH_FISH, "Kill Nyx", "Win Chat Reactions");
		assertEquals(Map.of("cod", CATCH_COD, "tropicalfish", CATCH_FISH),
				MasteryCatchTracker.activeCatchTargets());
	}

	/** "Tropical Fish" the item and "Tropicalfish" the challenge must land on one key. */
	@Test
	public void normalizeStripsSpacingAndCase() {
		assertEquals("tropicalfish", MasteryCatchTracker.normalize("Tropical Fish"));
		assertEquals("woodcrate", MasteryCatchTracker.normalize("Wood Crate"));
		assertEquals("", MasteryCatchTracker.normalize(null));
	}

	/** The opening inventory is a baseline: a bag of cod at login is not a haul. */
	@Test
	public void firstTickIsBaselineOnly() {
		active(CATCH_COD);
		assertFalse(MasteryCatchTracker.observe(Map.of(CATCH_COD, 64), FISHING));
		assertEquals(0, currentOf(CATCH_COD));
	}

	@Test
	public void aCatchWhileFishingIsCredited() {
		active(CATCH_COD);
		MasteryCatchTracker.observe(Map.of(CATCH_COD, 0), FISHING);
		assertTrue(MasteryCatchTracker.observe(Map.of(CATCH_COD, 1), FISHING));
		assertEquals(1, currentOf(CATCH_COD));
	}

	/** A stacked gain counts every item, not one per tick. */
	@Test
	public void aStackedGainCreditsEveryItem() {
		active(CATCH_COD);
		MasteryCatchTracker.observe(Map.of(CATCH_COD, 2), FISHING);
		MasteryCatchTracker.observe(Map.of(CATCH_COD, 5), FISHING);
		assertEquals(3, currentOf(CATCH_COD));
	}

	/** Buying cod with no rod out must not read as catching it. */
	@Test
	public void aGainWhileNotFishingIsNotCredited() {
		active(CATCH_COD);
		MasteryCatchTracker.observe(Map.of(CATCH_COD, 0), IDLE);
		assertFalse(MasteryCatchTracker.observe(Map.of(CATCH_COD, 64), IDLE));
		assertEquals(0, currentOf(CATCH_COD));
	}

	/** That shop purchase must also move the baseline, or the next cast credits it. */
	@Test
	public void anUncreditedGainStillMovesTheBaseline() {
		active(CATCH_COD);
		MasteryCatchTracker.observe(Map.of(CATCH_COD, 0), IDLE);
		MasteryCatchTracker.observe(Map.of(CATCH_COD, 64), IDLE);
		assertFalse(MasteryCatchTracker.observe(Map.of(CATCH_COD, 64), FISHING), "already the baseline");
		assertEquals(0, currentOf(CATCH_COD));
	}

	/** The bobber is gone the instant you reel in, but the catch arrives just after. */
	@Test
	public void aCatchLandingJustAfterReelInIsCredited() {
		active(CATCH_COD);
		MasteryCatchTracker.observe(Map.of(CATCH_COD, 0), FISHING);
		assertTrue(MasteryCatchTracker.observe(Map.of(CATCH_COD, 1), IDLE), "still inside the grace window");
		assertEquals(1, currentOf(CATCH_COD));
	}

	/** Once the grace window lapses, gains are shop purchases again. */
	@Test
	public void theGraceWindowExpires() {
		active(CATCH_COD);
		MasteryCatchTracker.observe(Map.of(CATCH_COD, 0), FISHING);
		for (int i = 0; i < MasteryCatchTracker.GRACE_TICKS; i++) {
			MasteryCatchTracker.observe(Map.of(CATCH_COD, 0), IDLE);
		}
		assertFalse(MasteryCatchTracker.observe(Map.of(CATCH_COD, 1), IDLE));
		assertEquals(0, currentOf(CATCH_COD));
	}

	/** Depositing the catch drops the count; picking it back up is not a new catch... */
	@Test
	public void aLossIsNotCredited() {
		active(CATCH_COD);
		MasteryCatchTracker.observe(Map.of(CATCH_COD, 10), FISHING);
		assertFalse(MasteryCatchTracker.observe(Map.of(CATCH_COD, 0), FISHING));
		assertEquals(0, currentOf(CATCH_COD));
	}

	@Test
	public void twoChallengesAreCreditedIndependently() {
		active(CATCH_COD, CATCH_FISH);
		MasteryCatchTracker.observe(Map.of(CATCH_COD, 0, CATCH_FISH, 0), FISHING);
		MasteryCatchTracker.observe(Map.of(CATCH_COD, 1, CATCH_FISH, 2), FISHING);
		assertEquals(1, currentOf(CATCH_COD));
		assertEquals(2, currentOf(CATCH_FISH));
	}

	/** An unselected challenge earns nothing even if the item shows up. */
	@Test
	public void anInactiveChallengeIsNotCredited() {
		active(CATCH_COD);
		MasteryCatchTracker.observe(Map.of(CATCH_FISH, 0), FISHING);
		assertFalse(MasteryCatchTracker.observe(Map.of(CATCH_FISH, 5), FISHING));
		assertEquals(0, currentOf(CATCH_COD));
	}

	@Test
	public void noCatchChallengeMeansEmptyTargets() {
		active("Kill Nyx");
		assertTrue(MasteryCatchTracker.activeCatchTargets().isEmpty());
	}

	/** Disconnecting clears the baseline, so rejoining re-baselines rather than crediting. */
	@Test
	public void resetDropsTheBaseline() {
		active(CATCH_COD);
		MasteryCatchTracker.observe(Map.of(CATCH_COD, 0), FISHING);
		MasteryCatchTracker.reset();
		assertFalse(MasteryCatchTracker.observe(Map.of(CATCH_COD, 30), FISHING));
		assertEquals(0, currentOf(CATCH_COD));
	}
}
