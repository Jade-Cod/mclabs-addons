package dev.jade.labsaddons.crate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pity curves against the server's own published figures.
 *
 * <p>Every {@code {roll, chance}} pair below was scraped out of {@code "Your <Rarity> odds"}
 * with the GUI dumper — 999 consecutive rolls of Very Rare, 1044 of Super Rare and 1215 of
 * Exceedingly Rare. The closed forms reproduced all 3258 of them exactly, so what is kept here
 * is a spread wide enough to pin each curve's shape and, for Exceedingly Rare, both seams: the
 * pairs either side of roll 11 and either side of the pity cliff at 250.
 *
 * <p>If MCLabs ever retunes a ladder these are the tests that will say so, which is worth more
 * than the arithmetic: a wrong curve here is a number a player would bet on.
 */
class CratePityTest {
	@BeforeEach
	void clearRollClock() {
		CratePity.forget();
	}

	@Test
	void veryRareIsTheSquareRootOfTheRollsBehindIt() {
		assertServer(CrateRarity.VERY_RARE, new double[][] {
				{1, 0.66}, {2, 1.0}, {3, 1.414}, {9, 2.828}, {10, 3.0}, {11, 3.162},
				{12, 3.317}, {13, 3.464}, {50, 7.0}, {100, 9.95}, {249, 15.748},
				{250, 15.78}, {251, 15.811}, {252, 15.843}, {500, 22.338}, {999, 31.591}
		});
	}

	@Test
	void superRareIsAShallowerRootOfTheSameShape() {
		assertServer(CrateRarity.SUPER_RARE, new double[][] {
				{1, 0.2}, {2, 0.33}, {3, 0.435}, {9, 0.758}, {10, 0.795}, {11, 0.829},
				{12, 0.861}, {13, 0.892}, {50, 1.565}, {100, 2.074}, {249, 2.994},
				{250, 2.999}, {251, 3.004}, {252, 3.009}, {500, 3.961}, {999, 5.226}
		});
	}

	/**
	 * The one that is three curves rather than one, which is why a single page of it reads as a
	 * flat line and why the first capture looked like a bug.
	 */
	@Test
	void exceedinglyRareCrawlsForTwoHundredRollsAndThenClimbs() {
		assertServer(CrateRarity.EXCEEDINGLY_RARE, new double[][] {
				{1, 0.02}, {2, 0.06}, {3, 0.091}, {9, 0.209}, {10, 0.224}, {11, 0.239},
				{12, 0.24}, {13, 0.241}, {50, 0.259}, {100, 0.268}, {249, 0.281},
				{250, 0.281}, {251, 0.28}, {252, 0.282}, {500, 0.689}, {999, 1.695}
		});
	}

	/**
	 * The cliff is a step <em>down</em>: the only place in 1215 captured values where the next
	 * roll is worse than the one before it. Worth its own test, because a curve fitted straight
	 * through it would look right everywhere else and be wrong for 240 rolls.
	 */
	@Test
	void theExceedinglyRareCliffAtTwoFiftyIsAStepDown() {
		double before = CratePity.chance(CrateRarity.EXCEEDINGLY_RARE, 250);
		double after = CratePity.chance(CrateRarity.EXCEEDINGLY_RARE, 251);
		assertTrue(after < before, "expected the cliff to dip, got " + before + " then " + after);
		assertEquals("0.281%", CratePity.format(before));
		assertEquals("0.28%", CratePity.format(after));
		// And it only dips once: past the cliff it climbs faster than anywhere before it.
		assertTrue(CratePity.chance(CrateRarity.EXCEEDINGLY_RARE, 400)
				> CratePity.chance(CrateRarity.EXCEEDINGLY_RARE, 251));
	}

	@Test
	void rareIsVeryRareThreeTimesOverAndTheTwoBelowHaveNoPity() {
		assertEquals("2%", CratePity.format(CratePity.chance(CrateRarity.RARE, 1)));
		assertEquals("3%", CratePity.format(CratePity.chance(CrateRarity.RARE, 2)));
		assertEquals("8.485%", CratePity.format(CratePity.chance(CrateRarity.RARE, 9)));
		assertEquals(CratePity.chance(CrateRarity.UNCOMMON, 1),
				CratePity.chance(CrateRarity.UNCOMMON, 900));
		assertEquals(100d, CratePity.chance(CrateRarity.COMMON, 7));
	}

	/** The server trims its own trailing zeros, and a board that does not looks like a different one. */
	@Test
	void chancesAreWrittenTheWayTheServerWritesThem() {
		assertEquals("0.66%", CratePity.format(0.66d));
		assertEquals("2%", CratePity.format(2d));
		assertEquals("0.277%", CratePity.format(0.2773d));
		assertEquals("100%", CratePity.format(100d));
	}

	// --- the counters --------------------------------------------------------

	@Test
	void aRollMovesEveryLadderAndTheWinnerGoesBackToOne() {
		Map<String, Integer> rolls = counts(190, 19, 4);
		Map<String, Integer> next = CratePity.rolled(rolls, CrateRarity.VERY_RARE);
		assertNotNull(next);
		assertEquals(191, CratePity.roll(next, CrateRarity.EXCEEDINGLY_RARE));
		assertEquals(20, CratePity.roll(next, CrateRarity.SUPER_RARE));
		assertEquals(1, CratePity.roll(next, CrateRarity.VERY_RARE));
		// The map handed in is untouched, so a caller that declines to save keeps its count.
		assertEquals(190, CratePity.roll(rolls, CrateRarity.EXCEEDINGLY_RARE));
	}

	/**
	 * Winning a rarity leaves the ones above it alone. Two captures a session apart had Very
	 * Rare sitting at roll #1 while Super Rare went from page 3 to page 5 and Exceedingly Rare
	 * from 22 to 25 — the ladders above a win keep climbing, which is what the server rolling
	 * from the rarest down has to look like.
	 */
	@Test
	void winningARarityDoesNotResetTheOnesAboveIt() {
		Map<String, Integer> next = CratePity.rolled(counts(190, 19, 4), CrateRarity.VERY_RARE);
		assertTrue(CratePity.roll(next, CrateRarity.SUPER_RARE) > 19);
		assertTrue(CratePity.roll(next, CrateRarity.EXCEEDINGLY_RARE) > 190);
	}

	/** A roll whose result we never saw still counts, because the server said one happened. */
	@Test
	void aRollWeDidNotWatchStillCountsAgainstEveryLadder() {
		Map<String, Integer> next = CratePity.rolled(counts(190, 19, 4), null);
		assertEquals(191, CratePity.roll(next, CrateRarity.EXCEEDINGLY_RARE));
		assertEquals(20, CratePity.roll(next, CrateRarity.SUPER_RARE));
		assertEquals(5, CratePity.roll(next, CrateRarity.VERY_RARE));
	}

	/**
	 * A ladder the odds menu has never been opened for is left out rather than started at one.
	 * Counting up from a number we do not have would put a figure on the board that reads like
	 * the server's and is not.
	 */
	@Test
	void anUnanchoredLadderIsNotInvented() {
		assertNull(CratePity.rolled(new LinkedHashMap<>(), CrateRarity.VERY_RARE));
		Map<String, Integer> onlyOne = new LinkedHashMap<>();
		onlyOne.put(CrateRarity.SUPER_RARE.name(), 19);
		Map<String, Integer> next = CratePity.rolled(onlyOne, null);
		assertEquals(20, CratePity.roll(next, CrateRarity.SUPER_RARE));
		assertEquals(0, CratePity.roll(next, CrateRarity.EXCEEDINGLY_RARE));
	}

	// --- anchoring off the odds menu -----------------------------------------

	/**
	 * The odds page states where you are exactly: a roll already spent is drawn as an opened
	 * crate and one still ahead as a closed one, so the first head past the opened ones is the
	 * roll about to happen. The figures here are the page as captured in-game — rolls #216 to
	 * #224 with #219 current, which is three spent and six ahead.
	 */
	@Test
	void theBoundaryBetweenSpentAndUnspentHeadsIsTheCurrentRoll() {
		assertEquals(219, CratePity.currentRoll(page(216, 3)));
		// The first roll of a page: eight ahead and one spent behind it.
		assertEquals(217, CratePity.currentRoll(page(216, 1)));
		// And the last: everything before it is gone.
		assertEquals(224, CratePity.currentRoll(page(216, 8)));
	}

	/**
	 * A page with nothing to mark cannot say where you are, and saying so is the whole point:
	 * paging back through the menu walks over pages that are entirely spent, and anchoring to
	 * the top of one of those would throw away a count with hundreds of rolls behind it.
	 */
	@Test
	void aPageWithNoBoundaryOnItSaysNothing() {
		assertEquals(0, CratePity.currentRoll(page(216, 9)));
		assertEquals(0, CratePity.currentRoll(page(216, 0)));
		assertEquals(0, CratePity.currentRoll(Map.of()));
		assertEquals(0, CratePity.currentRoll(null));
	}

	/** Two runs and no more. Three kinds of head is not this shape, and is read as nothing. */
	@Test
	void aPageThatIsNotTwoRunsSaysNothing() {
		Map<Integer, String> scrambled = new LinkedHashMap<>(page(216, 4));
		scrambled.put(217, "a third kind");
		assertEquals(0, CratePity.currentRoll(scrambled));
		Map<Integer, String> spentAgain = new LinkedHashMap<>(page(216, 4));
		spentAgain.put(222, "spent");
		assertEquals(0, CratePity.currentRoll(spentAgain));
	}

	@Test
	void anchoringTakesTheRollTheMenuNamesAndOnlyWhenItIsNews() {
		Map<String, Integer> anchored =
				CratePity.anchored(new LinkedHashMap<>(), CrateRarity.EXCEEDINGLY_RARE, 219);
		assertEquals(219, CratePity.roll(anchored, CrateRarity.EXCEEDINGLY_RARE));
		assertNull(CratePity.anchored(anchored, CrateRarity.EXCEEDINGLY_RARE, 219));
		assertNull(CratePity.anchored(anchored, CrateRarity.EXCEEDINGLY_RARE, 0));
		// A count that has drifted — rolls opened on another client, say — is corrected.
		Map<String, Integer> corrected =
				CratePity.anchored(anchored, CrateRarity.EXCEEDINGLY_RARE, 231);
		assertEquals(231, CratePity.roll(corrected, CrateRarity.EXCEEDINGLY_RARE));
	}

	@Test
	void theOddsMenuTitleNamesItsRarityAndNothingElseDoes() {
		assertEquals(CrateRarity.EXCEEDINGLY_RARE,
				CratePity.oddsMenuRarity("Your Exceedingly Rare odds"));
		assertEquals(CrateRarity.SUPER_RARE, CratePity.oddsMenuRarity("Your Super Rare odds"));
		assertEquals(CrateRarity.VERY_RARE, CratePity.oddsMenuRarity("Your Very Rare odds"));
		assertNull(CratePity.oddsMenuRarity("Supply Crate II"));
		assertNull(CratePity.oddsMenuRarity("Your odds"));
		assertNull(CratePity.oddsMenuRarity("Your Legendary odds"));
		assertNull(CratePity.oddsMenuRarity(null));
	}

	/**
	 * The page-turning heads sit in the same menu and their lore reads "Rolls #181 - #189", so
	 * anything lenient about the name would anchor a page out.
	 */
	@Test
	void onlyARollHeadNamesARoll() {
		assertEquals(190, CratePity.rollNumber("Roll #190"));
		assertEquals(1, CratePity.rollNumber("Roll #1"));
		assertEquals(0, CratePity.rollNumber("Previous Page (21)"));
		assertEquals(0, CratePity.rollNumber("Next Page (23)"));
		assertEquals(0, CratePity.rollNumber("Rolls #181 - #189"));
		assertEquals(0, CratePity.rollNumber("Roll #"));
		assertEquals(0, CratePity.rollNumber("Roll #12a"));
		assertEquals(0, CratePity.rollNumber("Info"));
	}

	// --- one roll, one count -------------------------------------------------

	@Test
	void theServersOwnLineIsWhatSaysARollHappened() {
		assertTrue(CratePity.isPityTick("[⚡ Your Exceedingly Rare odds have been JACKED-UP! ⚡]"));
		assertFalse(CratePity.isPityTick(
				"MCLabs » You opened a Supply Crate II and unboxed Smelling Salts!"));
		assertFalse(CratePity.isPityTick("Your Exceedingly Rare odds"));
		assertFalse(CratePity.isPityTick(null));
	}

	/**
	 * A watched spin and the line that follows it are one roll. Without this the board counted
	 * twice on every crate a player actually sat and watched, which is all of them.
	 */
	@Test
	void theLineFollowingAWatchedSpinIsTheSameRoll() {
		assertTrue(CratePity.isNewRoll(10_000L));
		CratePity.counted(10_000L);
		assertFalse(CratePity.isNewRoll(10_500L));
		assertFalse(CratePity.isNewRoll(15_000L));
		assertTrue(CratePity.isNewRoll(17_000L));
	}

	// --- helpers -------------------------------------------------------------

	/**
	 * Every scraped figure, compared at the three decimals the server prints and no further —
	 * rounded as integers rather than through {@link CratePity#format}, so a fault in the
	 * formatter cannot make a wrong curve agree with itself.
	 */
	private static void assertServer(CrateRarity rarity, double[][] published) {
		for (double[] pair : published) {
			int roll = (int) pair[0];
			assertEquals(Math.round(pair[1] * 1000d),
					Math.round(CratePity.chance(rarity, roll) * 1000d),
					rarity + " roll #" + roll);
		}
	}

	/** One page of nine heads from roll {@code first}, the first {@code spent} of them used. */
	private static Map<Integer, String> page(int first, int spent) {
		Map<Integer, String> heads = new LinkedHashMap<>();
		for (int i = 0; i < 9; i++) {
			heads.put(first + i, i < spent ? "spent" : "ahead");
		}
		return heads;
	}

	private static Map<String, Integer> counts(int exceedingly, int superRare, int veryRare) {
		Map<String, Integer> rolls = new LinkedHashMap<>();
		rolls.put(CrateRarity.EXCEEDINGLY_RARE.name(), exceedingly);
		rolls.put(CrateRarity.SUPER_RARE.name(), superRare);
		rolls.put(CrateRarity.VERY_RARE.name(), veryRare);
		return rolls;
	}
}
