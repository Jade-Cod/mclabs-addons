package dev.jade.fishbite.booster;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BoosterTrackerTest {

	@Test
	public void testParseDurationMs() {
		assertEquals(20 * 60 * 1000L, BoosterTracker.parseDurationMs("20m"));
		assertEquals((1 * 3600 + 30 * 60) * 1000L, BoosterTracker.parseDurationMs("1h30m"));
		assertEquals((13 * 60 + 53) * 1000L, BoosterTracker.parseDurationMs("13m:53s"));
		assertEquals(0L, BoosterTracker.parseDurationMs("invalid"));
	}

	@Test
	public void testFormatMultiplier() {
		assertEquals("2x", BoosterTracker.formatMultiplier(2.0));
		assertEquals("1.2x", BoosterTracker.formatMultiplier(1.2));
	}
}
