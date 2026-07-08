package dev.jade.labsaddons.cooldown;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CooldownEntryTest {

	@Test
	public void remainingCountsDownToZeroAndClamps() {
		CooldownEntry entry = new CooldownEntry("mcmmo:super_breaker", "Super Breaker", 10_000L, false, false,
				10_000L);
		assertEquals(10_000L, entry.remainingMs(0L));
		assertEquals(1L, entry.remainingMs(9_999L));
		assertEquals(0L, entry.remainingMs(10_000L));
		assertEquals(0L, entry.remainingMs(99_999L));
	}

	@Test
	public void readyOnlyWhenCooldownElapsedAndNotActive() {
		CooldownEntry cooling = new CooldownEntry("k", "Label", 5_000L, false, false, 5_000L);
		assertFalse(cooling.isReady(4_999L));
		assertTrue(cooling.isReady(5_000L));

		CooldownEntry active = new CooldownEntry("k", "Label", 0L, true, false, 0L);
		assertFalse(active.isReady(99_999L));
	}

	@Test
	public void elapsedFractionTracksRechargeProgressAndClamps() {
		CooldownEntry entry = new CooldownEntry("k", "Label", 10_000L, false, false, 10_000L);
		assertEquals(0f, entry.elapsedFraction(0L));
		assertEquals(0.5f, entry.elapsedFraction(5_000L));
		assertEquals(1f, entry.elapsedFraction(10_000L));
		// Lingers at 1 past readiness rather than overshooting.
		assertEquals(1f, entry.elapsedFraction(99_999L));

		// No known total (shouldn't happen for a real entry, but must not divide by zero).
		CooldownEntry noTotal = new CooldownEntry("k", "Label", 10_000L, false, false, 0L);
		assertEquals(1f, noTotal.elapsedFraction(0L));
	}
}
