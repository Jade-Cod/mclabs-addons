package dev.jade.labsaddons.coinflip;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CfOddsTest {
	private static final long DOLLAR = 100L;

	@Test
	void theMeasuredWinPaysOnePointNineTimesTheStake() {
		// Coinflip » You have won the $600,000 coinflip against _MikeHunt and received
		// $1,140,000!
		assertEquals(1_140_000 * DOLLAR, CfOdds.winReturnCents(600_000 * DOLLAR));
		assertEquals(540_000 * DOLLAR, CfOdds.winProfitCents(600_000 * DOLLAR));
	}

	@Test
	void theTaxIsFivePercentOfThePotNotTheProfit() {
		assertEquals(1_200_000 * DOLLAR, CfOdds.potCents(600_000 * DOLLAR));
		assertEquals(60_000 * DOLLAR, CfOdds.taxCents(600_000 * DOLLAR));
	}

	@Test
	void theEdgeIsFivePercentAtEveryWager() {
		for (long dollars : new long[] {200L, 750L, 600_000L, 100_000_000L}) {
			long wager = dollars * DOLLAR;
			// Half the tax, which is what a 50/50 game costs on average.
			assertEquals(CfOdds.taxCents(wager) / 2, CfOdds.expectedLossCents(wager),
					"edge at $" + dollars);
		}
	}

	@Test
	void investorPointsMatchBothMeasuredFlips() {
		// 1.75 points on a $750 flip, 1,250 on a $500,000 one: 0.25% of the wager, rounded
		// down to the nearest quarter point.
		assertEquals("1.75", CfOdds.investorPointsText(750 * DOLLAR));
		assertEquals("1,250", CfOdds.investorPointsText(500_000 * DOLLAR));
		assertEquals(7L, CfOdds.investorQuarterPoints(750 * DOLLAR));
	}

	@Test
	void theBiggestFlipTheServerAllowsDoesNotOverflow() {
		long max = 100_000_000L * DOLLAR;
		assertEquals(190_000_000L * DOLLAR, CfOdds.winReturnCents(max));
	}
}
