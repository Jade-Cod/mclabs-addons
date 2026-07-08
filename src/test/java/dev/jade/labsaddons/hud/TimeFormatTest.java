package dev.jade.labsaddons.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TimeFormatTest {
	@Test
	public void preciseShowsOneDecimalBelowAMinute() {
		assertEquals("5.3", TimeFormat.precise(5_340L));
		assertEquals("0.0", TimeFormat.precise(0L));
		assertEquals("59.9", TimeFormat.precise(59_949L));
	}

	@Test
	public void preciseRoundsToNearestTenth() {
		assertEquals("5.4", TimeFormat.precise(5_360L));
	}

	@Test
	public void preciseFallsBackToHmsAtAMinuteAndAbove() {
		assertEquals(TimeFormat.hms(60_000L), TimeFormat.precise(60_000L));
		assertEquals(TimeFormat.hms(125_000L), TimeFormat.precise(125_000L));
	}

	@Test
	public void preciseClampsNegativeToZero() {
		assertEquals("0.0", TimeFormat.precise(-500L));
	}
}
