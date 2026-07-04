package dev.jade.fishbite.cooldown;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CooldownEntryTest {

	@Test
	public void remainingCountsDownToZeroAndClamps() {
		CooldownEntry entry = new CooldownEntry("mcmmo:super_breaker", "Super Breaker", 10_000L, false, false);
		assertEquals(10_000L, entry.remainingMs(0L));
		assertEquals(1L, entry.remainingMs(9_999L));
		assertEquals(0L, entry.remainingMs(10_000L));
		assertEquals(0L, entry.remainingMs(99_999L));
	}

	@Test
	public void readyOnlyWhenCooldownElapsedAndNotActive() {
		CooldownEntry cooling = new CooldownEntry("k", "Label", 5_000L, false, false);
		assertFalse(cooling.isReady(4_999L));
		assertTrue(cooling.isReady(5_000L));

		CooldownEntry active = new CooldownEntry("k", "Label", 0L, true, false);
		assertFalse(active.isReady(99_999L));
	}
}
