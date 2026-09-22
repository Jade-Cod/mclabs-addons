package dev.jade.labsaddons.mines;

import dev.jade.labsaddons.casino.Money;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pinned against the four figures the server printed during the captured round: 25 tiles,
 * 9 mines, $3,500 staked. If the house rate ever moves, this is what says so.
 */
class MinesOddsTest {
	private static final long STAKE = Money.fromDollars(3_500L);
	private static final int MINES = 9;

	@Test
	void matchesEveryFigureTheServerPrinted() {
		// "for $3,828.25" — the exact figure is $3,828.125, which the server rounded up.
		assertEquals(382_813L, MinesOdds.payoutCents(STAKE, MINES, 1));
		assertEquals(612_500L, MinesOdds.payoutCents(STAKE, MINES, 2));
		assertEquals(1_006_250L, MinesOdds.payoutCents(STAKE, MINES, 3));
		// The server said $17,028.46 here, 39c under. Every other rung lands exactly.
		assertEquals(1_702_885L, MinesOdds.payoutCents(STAKE, MINES, 4));
	}

	@Test
	void payoutIsSeventyPercentOfFair() {
		for (int mines = 1; mines < 24; mines++) {
			for (int stars = 1; stars <= Math.min(5, MinesOdds.safeTiles(mines)); stars++) {
				assertEquals(MinesOdds.fair(mines, stars) * MinesOdds.RETURN_RATE,
						MinesOdds.multiplier(mines, stars), 1e-9);
			}
		}
	}

	/**
	 * The point of the whole thing: a flat cut at every rung means cashing out and
	 * revealing again are worth the same, so the board never has to recommend one.
	 */
	@Test
	void everyRungHasTheSameExpectedValue() {
		for (int stars = 0; stars < 5; stars++) {
			double survives = 1.0 - MinesOdds.mineChance(MINES, stars);
			double here = MinesOdds.multiplier(MINES, stars);
			double next = MinesOdds.multiplier(MINES, stars + 1);
			// Taking one more tile: survive it and collect the next rung, or lose it all.
			assertEquals(here, survives * next, 1e-9);
		}
	}

	@Test
	void lowMineCountsPayBackLessThanTheStake() {
		// Worth saying on the board: at three mines the first two rungs are a loss even
		// when you win them, and the server never mentions it.
		assertTrue(MinesOdds.multiplier(3, 1) < 1.0);
		assertTrue(MinesOdds.multiplier(3, 2) < 1.0);
		assertTrue(MinesOdds.multiplier(3, 3) > 1.0);
	}

	@Test
	void mineChanceIsExact() {
		assertEquals(9 / 25.0, MinesOdds.mineChance(9, 0), 1e-9);
		assertEquals(9 / 22.0, MinesOdds.mineChance(9, 3), 1e-9);
		assertEquals(1.0, MinesOdds.mineChance(24, 24), 1e-9);
	}

	@Test
	void refusesCountsThatCannotHappen() {
		assertEquals(0.0, MinesOdds.fair(9, 17));
		assertEquals(0.0, MinesOdds.fair(25, 1));
		assertEquals(0.0, MinesOdds.fair(-1, 1));
		assertEquals(1.0, MinesOdds.fair(9, 0));
	}

	@Test
	void labelsTheLadder() {
		// Exactly 2.875, which a running product of doubles rounded down to 2.87.
		assertEquals("2.88x", MinesOdds.multiplierText(9, 3));
		assertEquals("1.09x", MinesOdds.multiplierText(9, 1));
		assertEquals("4.87x", MinesOdds.multiplierText(9, 4));
		assertEquals("8.51x", MinesOdds.multiplierText(9, 5));
	}

	@Test
	void anAbsurdStakeStillReturnsSomething() {
		// The exact path overflows well past any real balance; it must not wrap.
		long huge = Money.fromDollars(500_000_000_000L);
		assertTrue(MinesOdds.payoutCents(huge, 9, 12) > huge);
	}
}
