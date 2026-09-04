package dev.jade.labsaddons.raidmine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RaidMineFormatTest {
	@Test
	public void wholeAmountsGetNoDecimals() {
		assertEquals("3,032", RaidMineHudObject.amount(3032));
		assertEquals("0", RaidMineHudObject.amount(0));
	}

	@Test
	public void oneDecimalStaysOneDecimal() {
		assertEquals("14.2", RaidMineHudObject.amount(14.2));
		assertEquals("0.1", RaidMineHudObject.amount(0.1));
	}

	@Test
	public void multiplierTotalsKeepTheirSecondDecimal() {
		// A 1.25x item turns a base 5 drop into 6.25; rounding to one decimal would
		// show 6.3 and drift further with every drop added.
		assertEquals("6.25", RaidMineHudObject.amount(6.25));
		assertEquals("7.75", RaidMineHudObject.amount(1.25 * 6.2));
	}

	@Test
	public void largeFractionalTotalsStayGrouped() {
		assertEquals("12,345.75", RaidMineHudObject.amount(12345.75));
	}

	@Test
	public void ratesAreWholeNumbers() {
		// Extrapolated from a short sample; decimals would imply precision we lack.
		assertEquals("159,121/h", RaidMineHudObject.rate(159120.7));
		assertEquals("22,619/h", RaidMineHudObject.rate(22619.1));
	}

	@Test
	public void noRateUntilThereIsOne() {
		assertEquals("", RaidMineHudObject.rate(0));
	}
}
