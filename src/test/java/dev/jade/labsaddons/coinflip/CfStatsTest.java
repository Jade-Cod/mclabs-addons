package dev.jade.labsaddons.coinflip;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CfStatsTest {
	private static final long DOLLAR = 100L;

	/** The five lines of /cf stats, exactly as the server sends them, one message each. */
	private static final String[] BLOCK = {
			"│ Coinflips Played: 283",
			"│ Coinflips Won: 130",
			"│ Total Wagered: 1,231,357,591.65",
			"│ Total Winnings: $926,295,278.54",
			"│ Profit: -$305,062,313.11",
	};

	private static CfStats.Record parsed() {
		CfStats.Record record = CfStats.Record.EMPTY;
		for (String line : BLOCK) {
			record = CfStats.apply(record, line);
		}
		return record;
	}

	@Test
	void readsTheWholeBlockOneLineAtATime() {
		CfStats.Record record = parsed();
		assertEquals(283, record.played());
		assertEquals(130, record.won());
		assertEquals(153, record.lost());
		// Total Wagered is the one line the server prints with no dollar sign.
		assertEquals(123_135_759_165L, record.wageredCents());
		assertEquals(92_629_527_854L, record.winningsCents());
		assertEquals("45.9%", record.winRateText());
	}

	@Test
	void theProfitMatchesTheLineWeDeliberatelyDoNotParse() {
		// "Profit: -$305,062,313.11" is winnings minus wagered, so it is derived rather
		// than read: one less pattern to keep in step.
		assertEquals(-30_506_231_311L, parsed().profitCents());
	}

	@Test
	void theLossSplitsIntoTheTaxAndTheCoin() {
		CfStats.Record record = parsed();
		// 5% of everything ever wagered was never yours.
		assertEquals(-6_156_787_958L, record.taxCostCents());
		assertEquals(record.profitCents() - record.taxCostCents(), record.luckCents());
		assertTrue(record.luckCents() < record.taxCostCents(),
				"the coin cost more than the house did");
		assertEquals(-1.367, record.sigma(), 0.001);
	}

	@Test
	void aFlipFoldsIntoTheRecordWithoutAskingTheServerAgain() {
		CfStats.Record before = parsed();
		CfStats.Record afterWin = before.plusWin(600_000 * DOLLAR, 1_140_000 * DOLLAR);
		assertEquals(284, afterWin.played());
		assertEquals(131, afterWin.won());
		assertEquals(before.profitCents() + 540_000 * DOLLAR, afterWin.profitCents());

		CfStats.Record afterLoss = before.plusLoss(1_500_000 * DOLLAR);
		assertEquals(284, afterLoss.played());
		assertEquals(130, afterLoss.won());
		assertEquals(before.profitCents() - 1_500_000 * DOLLAR, afterLoss.profitCents());
	}

	@Test
	void anEmptyRecordSaysSoRatherThanDividingByZero() {
		assertTrue(CfStats.Record.EMPTY.isEmpty());
		assertEquals("—", CfStats.Record.EMPTY.winRateText());
		assertEquals(0d, CfStats.Record.EMPTY.sigma());
		assertEquals(0L, CfStats.Record.EMPTY.luckCents());
	}

	@Test
	void anUnrelatedLineChangesNothing() {
		CfStats.Record record = parsed();
		assertEquals(record, CfStats.apply(record, "Coinflip » _MikeHunt has just won a "
				+ "$60,000,000 coinflip against twinpenguinlord! Play with /cf"));
	}
}
