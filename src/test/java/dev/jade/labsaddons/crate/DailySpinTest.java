package dev.jade.labsaddons.crate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every string here is one the server actually sent, captured on 2026-09-21 across two
 * daily spins.
 */
class DailySpinTest {
	private static final String MENU = "Daily Bonus (98 day streak)";
	private static final String ROLL = "Rolling rewards...";
	private static final String CHOICE = "Choose a reward!";
	private static final long T = 1_000L;

	@BeforeEach
	void forget() {
		DailySpin.reset();
	}

	@Test
	void theDailyMenuNamesItsStreak() {
		assertEquals(98, DailySpin.streakOf(MENU));
		assertEquals(99, DailySpin.streakOf("Daily Bonus (99 day streak)"));
		assertEquals(1, DailySpin.streakOf("Daily Bonus (1 day streak)"));
		assertEquals(1234, DailySpin.streakOf("Daily Bonus (1,234 day streak)"));
	}

	@Test
	void nothingElseNamesAStreak() {
		assertEquals(0, DailySpin.streakOf(ROLL));
		assertEquals(0, DailySpin.streakOf(CHOICE));
		assertEquals(0, DailySpin.streakOf("Voter Crate"));
		assertEquals(0, DailySpin.streakOf(""));
		assertEquals(0, DailySpin.streakOf(null));
	}

	/** The daily menu hands straight over to the roll, and the roll keeps the arm. */
	@Test
	void aRollOpenedFromTheDailyMenuIsTheDailySpin() {
		DailySpin.onScreen(MENU, T);
		DailySpin.onScreen(ROLL, T);
		assertTrue(DailySpin.isSpinning(T));
		assertEquals(98, DailySpin.streak());
		DailySpin.onScreen(CHOICE, T);
		assertTrue(DailySpin.isSpinning(T), "the choice is still the same spin");
	}

	/** A voter crate is rolled from no menu at all, so nothing ever armed it. */
	@Test
	void aRollOpenedFromNothingIsAVoterCrate() {
		DailySpin.onScreen(ROLL, T);
		assertFalse(DailySpin.isSpinning(T));
		assertEquals(0, DailySpin.streak());
	}

	/**
	 * The one that would otherwise bite: roll the daily, take a reward, then open a voter
	 * crate. Without the server's own line ending it, that crate would wear the daily's
	 * label and the previous day's streak.
	 */
	@Test
	void aVoterCrateAfterTheDailyIsNotTheDaily() {
		DailySpin.onScreen(MENU, T);
		DailySpin.onScreen(ROLL, T);
		DailySpin.onMessage("MCLabs » You rolled your Daily Spin and chose High Roller Cash Roll!");
		assertFalse(DailySpin.isSpinning(T));
		DailySpin.onScreen(ROLL, T);
		assertFalse(DailySpin.isSpinning(T));
	}

	@Test
	void anyOtherMenuEndsItToo() {
		DailySpin.onScreen(MENU, T);
		DailySpin.onScreen("Your Exceedingly Rare odds", T);
		assertFalse(DailySpin.isSpinning(T));
	}

	/** A choice walked away from cannot leave the next crate wearing the label for ever. */
	@Test
	void anAbandonedSpinTimesOut() {
		DailySpin.onScreen(MENU, T);
		assertTrue(DailySpin.isSpinning(T + 119_000L));
		assertFalse(DailySpin.isSpinning(T + 121_000L));
	}

	@Test
	void aPlayerRepeatingTheLineEndsNothing() {
		DailySpin.onScreen(MENU, T);
		DailySpin.onMessage(
				"[S] [VIP+] Someone: MCLabs » You rolled your Daily Spin and chose a thing!");
		assertTrue(DailySpin.isSpinning(T), "only the server's own line ends the spin");
	}

	@Test
	void theSecondSpinOfTheDayReplacesTheFirstsStreak() {
		DailySpin.onScreen(MENU, T);
		DailySpin.onScreen("Daily Bonus (99 day streak)", T + 5_000L);
		assertEquals(99, DailySpin.streak());
	}
}
