package dev.jade.labsaddons.raidmine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RaidMineFormatTest {
	@Test
	public void smallAmountsAreShownExactly() {
		assertEquals("966", RaidMineHudObject.amount(966));
		assertEquals("0", RaidMineHudObject.amount(0));
		assertEquals("999", RaidMineHudObject.amount(999));
	}

	@Test
	public void multiplierTotalsKeepTheirSecondDecimal() {
		// A 1.25x item turns a base 5 drop into 6.25. Below a thousand nothing is
		// compacted, so the exact figure survives.
		assertEquals("6.25", RaidMineHudObject.amount(6.25));
		assertEquals("14.2", RaidMineHudObject.amount(14.2));
		assertEquals("0.1", RaidMineHudObject.amount(0.1));
	}

	@Test
	public void thousandsBecomeK() {
		assertEquals("63.9k", RaidMineHudObject.amount(63_900));
		assertEquals("1k", RaidMineHudObject.amount(1_000));
		assertEquals("12.3k", RaidMineHudObject.amount(12_345));
	}

	@Test
	public void millionsBecomeM() {
		assertEquals("1.32m", RaidMineHudObject.amount(1_315_894));
		assertEquals("1m", RaidMineHudObject.amount(1_000_000));
	}

	@Test
	public void billionsBecomeB() {
		assertEquals("2.5b", RaidMineHudObject.amount(2_500_000_000d));
	}

	@Test
	public void compactedFiguresCarryNoDeadZeros() {
		// 64.0k and 1.00m would both be noise on a widget read at a glance.
		assertEquals("64k", RaidMineHudObject.amount(63_966));
		assertEquals("2m", RaidMineHudObject.amount(2_000_000));
		assertEquals("1.5k", RaidMineHudObject.amount(1_500));
	}

	@Test
	public void ratesAreCompactedToo() {
		assertEquals("1.32m/h", RaidMineHudObject.rate(1_315_894.6));
		assertEquals("13.6k/h", RaidMineHudObject.rate(13_556.8));
		assertEquals("123/h", RaidMineHudObject.rate(123.4));
	}

	@Test
	public void noRateUntilThereIsOne() {
		assertEquals("", RaidMineHudObject.rate(0));
	}
}
