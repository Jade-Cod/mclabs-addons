package dev.jade.fishbite.mcmmo;

import dev.jade.fishbite.cooldown.CooldownEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class McmmoCooldownTrackerTest {
	private static final long NOW = 1_000_000L;

	@BeforeEach
	public void reset() {
		McmmoCooldownTracker.clear();
		McmmoCooldownTracker.setHeldToolResolver(() -> null);
	}

	private static CooldownEntry only(long nowMs) {
		List<CooldownEntry> entries = McmmoCooldownTracker.entries(nowMs);
		assertEquals(1, entries.size());
		return entries.get(0);
	}

	@Test
	public void activationShowsActiveEntry() {
		McmmoCooldownTracker.onMessage("**SUPER BREAKER ACTIVATED**", NOW);
		CooldownEntry entry = only(NOW);
		assertEquals("Super Breaker", entry.label());
		assertTrue(entry.active());
	}

	@Test
	public void wornOffStartsDefaultCooldown() {
		McmmoCooldownTracker.onMessage("**Super Breaker has worn off**", NOW);
		CooldownEntry entry = only(NOW);
		assertFalse(entry.active());
		assertEquals(McmmoCooldownTracker.DEFAULT_COOLDOWN_MS, entry.remainingMs(NOW));
		// Seeded from the default, not server-stated: flagged so the HUD shows "~".
		assertTrue(entry.approximate());
	}

	@Test
	public void wornOffForAnotherPlayerIsIgnored() {
		McmmoCooldownTracker.onMessage("Super Breaker has worn off for SomeoneElse", NOW);
		assertTrue(McmmoCooldownTracker.entries(NOW).isEmpty());
	}

	@Test
	public void tooTiredSyncsExactCooldownViaHeldTool() {
		McmmoCooldownTracker.setHeldToolResolver(() -> McmmoAbility.Tool.PICKAXE);
		McmmoCooldownTracker.onMessage("You are too tired to use that ability again. (45s)", NOW);
		CooldownEntry entry = only(NOW);
		assertEquals("Super Breaker", entry.label());
		assertEquals(45_000L, entry.remainingMs(NOW));
		assertFalse(entry.approximate());
	}

	@Test
	public void tooTiredWithAmbiguousToolIsSkippedUnlessOneIsTracked() {
		McmmoCooldownTracker.setHeldToolResolver(() -> McmmoAbility.Tool.AXE);
		// Axe maps to both Tree Feller and Skull Splitter: no entry, no guess.
		McmmoCooldownTracker.onMessage("You are too tired to use that ability again. (30s)", NOW);
		assertTrue(McmmoCooldownTracker.entries(NOW).isEmpty());

		// Once exactly one of the two is already tracked, the sync refines it.
		McmmoCooldownTracker.onMessage("**Tree Feller has worn off**", NOW);
		McmmoCooldownTracker.onMessage("You are too tired to use that ability again. (30s)", NOW);
		CooldownEntry entry = only(NOW);
		assertEquals("Tree Feller", entry.label());
		assertEquals(30_000L, entry.remainingMs(NOW));
	}

	@Test
	public void readyExtraLineSyncsNamedCooldown() {
		McmmoCooldownTracker.onMessage("You ready your Axe. (Skull Splitter is on cooldown for 73s)", NOW);
		CooldownEntry entry = only(NOW);
		assertEquals("Skull Splitter", entry.label());
		assertEquals(73_000L, entry.remainingMs(NOW));
	}

	@Test
	public void mccooldownRowsSyncAndClear() {
		McmmoCooldownTracker.onMessage("Giga Drill Breaker - 118 seconds left", NOW);
		CooldownEntry entry = only(NOW);
		assertEquals("Giga Drill Breaker", entry.label());
		assertEquals(118_000L, entry.remainingMs(NOW));

		// A "Ready!" row removes the entry silently (it was never counting here).
		McmmoCooldownTracker.onMessage("Giga Drill Breaker - Ready!", NOW);
		assertTrue(McmmoCooldownTracker.entries(NOW).isEmpty());
	}

	@Test
	public void refreshedShowsReadyPulseThenDisappears() {
		McmmoCooldownTracker.onMessage("**Berserk has worn off**", NOW);
		McmmoCooldownTracker.onMessage("Your Berserk ability is refreshed!", NOW + 5_000L);

		CooldownEntry entry = only(NOW + 5_000L);
		assertTrue(entry.isReady(NOW + 5_000L));
		// Still visible within the linger window...
		assertEquals(1, McmmoCooldownTracker.entries(NOW + 5_000L + McmmoCooldownTracker.READY_LINGER_MS - 1).size());
		// ...gone after it.
		assertTrue(McmmoCooldownTracker.entries(NOW + 5_000L + McmmoCooldownTracker.READY_LINGER_MS + 1).isEmpty());
	}

	@Test
	public void expiredCooldownLingersAsReadyThenDisappears() {
		McmmoCooldownTracker.onMessage("Super Breaker - 10 seconds left", NOW);
		long readyAt = NOW + 10_000L;
		CooldownEntry entry = only(readyAt + 1);
		assertTrue(entry.isReady(readyAt + 1));
		assertTrue(McmmoCooldownTracker.entries(readyAt + McmmoCooldownTracker.READY_LINGER_MS + 1_001L).isEmpty());
	}

	@Test
	public void activationReplacesPendingCooldown() {
		McmmoCooldownTracker.onMessage("Green Terra - 5 seconds left", NOW);
		McmmoCooldownTracker.onMessage("**GREEN TERRA ACTIVATED**", NOW + 6_000L);
		CooldownEntry entry = only(NOW + 6_000L);
		assertTrue(entry.active());
	}

	@Test
	public void abilitiesRefreshedClearsEverything() {
		McmmoCooldownTracker.onMessage("Super Breaker - 100 seconds left", NOW);
		McmmoCooldownTracker.onMessage("**Tree Feller has worn off**", NOW);
		McmmoCooldownTracker.onMessage("**ABILITIES REFRESHED!**", NOW);
		assertTrue(McmmoCooldownTracker.entries(NOW).isEmpty());
	}

	@Test
	public void unrelatedChatIsIgnored() {
		McmmoCooldownTracker.onMessage("Booster activated! Sugcarronide boosted 1.2x by Ophiliah for 30m", NOW);
		McmmoCooldownTracker.onMessage("You caught a fish!", NOW);
		assertTrue(McmmoCooldownTracker.entries(NOW).isEmpty());
	}

	@Test
	public void entriesSortActiveFirstThenSoonest() {
		McmmoCooldownTracker.onMessage("Super Breaker - 200 seconds left", NOW);
		McmmoCooldownTracker.onMessage("Giga Drill Breaker - 50 seconds left", NOW);
		McmmoCooldownTracker.onMessage("**GREEN TERRA ACTIVATED**", NOW);
		List<CooldownEntry> entries = McmmoCooldownTracker.entries(NOW);
		assertEquals(3, entries.size());
		assertEquals("Green Terra", entries.get(0).label());
		assertEquals("Giga Drill Breaker", entries.get(1).label());
		assertEquals("Super Breaker", entries.get(2).label());
	}
}
