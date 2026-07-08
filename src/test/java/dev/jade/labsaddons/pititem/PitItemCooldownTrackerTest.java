package dev.jade.labsaddons.pititem;

import dev.jade.labsaddons.cooldown.CooldownEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PitItemCooldownTrackerTest {
	private static final long NOW = 1_000_000L;

	@BeforeEach
	public void reset() {
		PitItemCooldownTracker.clear();
	}

	private static CooldownEntry only(long nowMs) {
		List<CooldownEntry> entries = PitItemCooldownTracker.entries(nowMs);
		assertEquals(1, entries.size());
		return entries.get(0);
	}

	@Test
	public void stormbreakerChatStartsNominalCooldown() {
		PitItemCooldownTracker.onMessage("The Pit » The sky darkens as Stormbreaker summons thunder...", NOW);
		CooldownEntry entry = only(NOW);
		assertEquals("Stormbreaker", entry.label());
		assertEquals(45_000L, entry.remainingMs(NOW));
		assertTrue(entry.approximate());
	}

	@Test
	public void heavySteelChestplateChatStartsNominalCooldown() {
		PitItemCooldownTracker.onMessage("The Pit » You summon a friendly Possessed Armour!", NOW);
		CooldownEntry entry = only(NOW);
		assertEquals("Heavy Steel Chestplate", entry.label());
		assertEquals(30_000L, entry.remainingMs(NOW));
	}

	@Test
	public void blinkBootsChatStartsNominalCooldown() {
		PitItemCooldownTracker.onMessage("The Pit » You blink forward!", NOW);
		CooldownEntry entry = only(NOW);
		assertEquals("Blink Boots", entry.label());
		assertEquals(10_000L, entry.remainingMs(NOW));
	}

	@Test
	public void excaliburChatStartsNominalCooldown() {
		PitItemCooldownTracker.onMessage("The Pit » Excalibur channels divine power...", NOW);
		CooldownEntry entry = only(NOW);
		assertEquals("Excalibur", entry.label());
		assertEquals(30_000L, entry.remainingMs(NOW));
	}

	@Test
	public void actionbarStartsExactCooldownForChatlessItems() {
		PitItemCooldownTracker.onMessage("Body Slam [14s]", NOW);
		CooldownEntry entry = only(NOW);
		assertEquals("Body Slam", entry.label());
		assertEquals(14_000L, entry.remainingMs(NOW));
		assertFalse(entry.approximate());

		PitItemCooldownTracker.clear();
		PitItemCooldownTracker.onMessage("Scythe Sweep [28s]", NOW);
		entry = only(NOW);
		assertEquals("Scythe Sweep", entry.label());
		assertEquals(28_000L, entry.remainingMs(NOW));
	}

	@Test
	public void actionbarCorrectsTrackedItemWithoutResettingRingTotal() {
		PitItemCooldownTracker.onMessage("The Pit » Excalibur channels divine power...", NOW);
		long correctionAt = NOW + 10_000L;
		PitItemCooldownTracker.onMessage("Excalibur [18s]", correctionAt);
		CooldownEntry entry = only(correctionAt);
		assertEquals(18_000L, entry.remainingMs(correctionAt));
		// Total stays the original 30s nominal, not the 18s remaining, or the ring's
		// recharge progress would snap back to 0% on every actionbar correction.
		assertEquals(30_000L, entry.totalMs());
		assertFalse(entry.approximate());
	}

	@Test
	public void unrelatedTextIsIgnored() {
		PitItemCooldownTracker.onMessage("Super Breaker - 100 seconds left", NOW);
		PitItemCooldownTracker.onMessage("You caught a fish!", NOW);
		assertTrue(PitItemCooldownTracker.entries(NOW).isEmpty());
	}

	@Test
	public void expiredCooldownLingersAsReadyThenDisappears() {
		PitItemCooldownTracker.onMessage("Body Slam [15s]", NOW);
		long readyAt = NOW + 15_000L;
		CooldownEntry entry = only(readyAt + 1);
		assertTrue(entry.isReady(readyAt + 1));
		assertTrue(PitItemCooldownTracker.entries(readyAt + PitItemCooldownTracker.READY_LINGER_MS + 1_001L).isEmpty());
	}
}
