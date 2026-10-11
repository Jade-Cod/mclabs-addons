package dev.jade.labsaddons.config;

import dev.jade.labsaddons.rental.RentalEntry;
import dev.jade.labsaddons.rental.RentalLore;
import dev.jade.labsaddons.rental.RentalTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** In the config package for {@code useStore}, like {@link PrestigeRestartTest}. */
class RentalTrackerTest {
	private static final String BOAT = "minecraft:acacia_boat";
	private static final long NOW = 1_000_000_000L;
	private static final long HOUR = 3_600_000L;

	@TempDir
	Path configDir;

	@BeforeEach
	void freshConfig() {
		LabsAddonsConfig.useStore(new ConfigStore(configDir));
		RentalTracker.clear();
	}

	private static RentalLore.Rental raft(long endMs) {
		return new RentalLore.Rental("Portable Raft", "Zemperus", endMs);
	}

	private static long onlyEnd() {
		List<RentalEntry> entries = RentalTracker.entries();
		assertEquals(1, entries.size());
		return entries.get(0).endMs;
	}

	@Test
	void aStaleItemWindowNeverUndoesAnExtension() {
		RentalTracker.onHeld(BOAT, raft(NOW + 2 * HOUR), NOW);
		RentalTracker.onMessage("Item Rental » You have paid $10,000 to extend your rent of Portable Raft"
				+ " from Zemperus for another 1 hour. Your rental now ends in 02h:50m.", NOW);
		RentalTracker.onHeld(BOAT, raft(NOW + 2 * HOUR), NOW + 1_000L);
		assertEquals(NOW + (2 * 60 + 50) * 60_000L, onlyEnd());
	}

	@Test
	void theReturnLineClearsItAndTheLeftoverItemIsNotReadBackIn() {
		RentalTracker.onHeld(BOAT, raft(NOW + HOUR), NOW);
		RentalTracker.onMessage("Item Rental » You have returned the rented Portable Raft to Zemperus. Thank you!", NOW);
		RentalTracker.onHeld(BOAT, raft(NOW + HOUR), NOW + 1_000L);
		assertTrue(RentalTracker.entries().isEmpty());
		// Renting it again later is a new rental.
		RentalTracker.onHeld(BOAT, raft(NOW + 3 * HOUR), NOW + 60_000L);
		assertEquals(NOW + 3 * HOUR, onlyEnd());
	}

	@Test
	void theReturnMenuIsTheWholeListAndKeepsKnownIcons() {
		RentalTracker.onHeld(BOAT, raft(NOW + HOUR), NOW);
		RentalTracker.onHeld("minecraft:diamond_boots", new RentalLore.Rental("Speed Boots", "Alex", NOW + HOUR), NOW);
		RentalTracker.reconcile(List.of(new RentalEntry("", "Portable Raft", "Zemperus", NOW + 2 * HOUR)));
		List<RentalEntry> entries = RentalTracker.entries();
		assertEquals(1, entries.size());
		assertEquals(BOAT, entries.get(0).itemId);
		assertEquals(NOW + 2 * HOUR, entries.get(0).endMs);
	}

	@Test
	void entriesComeSoonestFirst() {
		RentalTracker.onHeld(BOAT, raft(NOW + 3 * HOUR), NOW);
		RentalTracker.onHeld(BOAT, new RentalLore.Rental("Speed Boots", "Alex", NOW + HOUR), NOW);
		assertEquals(List.of("Speed Boots", "Portable Raft"),
				RentalTracker.entries().stream().map(e -> e.name).toList());
	}

	private static final String EXTEND_1H = "Item Rental » You have paid $10,000 to extend your rent of Portable Raft"
			+ " from Zemperus for another 1 hour. Your rental now ends in %s. Return the item by the end of your"
			+ " rental time with /rent return.";

	@Test
	void anExtendSetsTheEndToNowPlusTheTimeLeftNotTheTimeAdded() {
		RentalTracker.onHeld(BOAT, raft(NOW + HOUR), NOW);
		RentalTracker.onMessage(EXTEND_1H.formatted("02h:50m"), NOW + 5_000L);
		assertEquals(NOW + 5_000L + (2 * 60 + 50) * 60_000L, onlyEnd());
	}

	@Test
	void anExtendWithADayPartMovesTheEndByADayAndMore() {
		RentalTracker.onHeld(BOAT, raft(NOW + HOUR), NOW);
		RentalTracker.onMessage(EXTEND_1H.formatted("1d:02h:50m"), NOW);
		assertEquals(NOW + ((24 + 2) * 60 + 50) * 60_000L, onlyEnd());
	}

	@Test
	void anExtendUnderAnHourParses() {
		RentalTracker.onHeld(BOAT, raft(NOW + 10 * 60_000L), NOW);
		RentalTracker.onMessage(EXTEND_1H.formatted("59m"), NOW);
		assertEquals(NOW + 59 * 60_000L, onlyEnd());
	}

	@Test
	void anExtendKeepsTheIconAndOwner() {
		RentalTracker.onHeld(BOAT, raft(NOW + HOUR), NOW);
		RentalTracker.onMessage(EXTEND_1H.formatted("02h:50m"), NOW);
		RentalEntry entry = RentalTracker.entries().get(0);
		assertEquals(BOAT, entry.itemId);
		assertEquals("Zemperus", entry.owner);
	}

	@Test
	void anExtendForARentalWeAreNotTrackingAddsNothing() {
		RentalTracker.onMessage(EXTEND_1H.formatted("02h:50m"), NOW);
		assertTrue(RentalTracker.entries().isEmpty());
	}

	@Test
	void anExtendTouchesOnlyTheMatchingNameAndOwner() {
		RentalTracker.onHeld(BOAT, raft(NOW + HOUR), NOW);
		RentalTracker.onHeld(BOAT, new RentalLore.Rental("Portable Raft", "Alex", NOW + HOUR), NOW);
		RentalTracker.onMessage(EXTEND_1H.formatted("02h:50m"), NOW);
		assertEquals(List.of(NOW + HOUR, NOW + (2 * 60 + 50) * 60_000L),
				RentalTracker.entries().stream().map(e -> e.endMs).toList());
	}

	@Test
	void theRentConfirmationLineChangesNothing() {
		RentalTracker.onHeld(BOAT, raft(NOW + HOUR), NOW);
		RentalTracker.onMessage("Item Rental » You have paid $20,000 to rent Portable Raft from Zemperus for 2 hours"
				+ " (contract #3084). Return the item by the end of your rental time with /rent return.", NOW);
		assertEquals(NOW + HOUR, onlyEnd());
	}

	@Test
	void theReturnLineForAnotherOwnerLeavesThisRentalAlone() {
		RentalTracker.onHeld(BOAT, raft(NOW + HOUR), NOW);
		RentalTracker.onMessage("Item Rental » You have returned the rented Portable Raft to Alex. Thank you!", NOW);
		assertEquals(NOW + HOUR, onlyEnd());
	}

	@Test
	void theItemIsReadBackInOnceTheReturnGraceHasPassed() {
		RentalTracker.onHeld(BOAT, raft(NOW + HOUR), NOW);
		RentalTracker.onMessage("Item Rental » You have returned the rented Portable Raft to Zemperus. Thank you!", NOW);
		RentalTracker.onHeld(BOAT, raft(NOW + HOUR), NOW + 4_999L);
		assertTrue(RentalTracker.entries().isEmpty());
		RentalTracker.onHeld(BOAT, raft(NOW + HOUR), NOW + 5_000L);
		assertEquals(NOW + HOUR, onlyEnd());
	}

	@Test
	void theItemWindowOnlyEverMovesTheEndLater() {
		RentalTracker.onHeld(BOAT, raft(NOW + 2 * HOUR), NOW);
		RentalTracker.onHeld(BOAT, raft(NOW + HOUR), NOW + 1_000L);
		assertEquals(NOW + 2 * HOUR, onlyEnd());
		RentalTracker.onHeld(BOAT, raft(NOW + 3 * HOUR), NOW + 2_000L);
		assertEquals(NOW + 3 * HOUR, onlyEnd());
	}

	@Test
	void seeingTheItemAfterTheMenuFillsInTheIcon() {
		RentalTracker.reconcile(List.of(new RentalEntry("", "Portable Raft", "Zemperus", NOW + HOUR)));
		assertEquals("", RentalTracker.entries().get(0).itemId);
		RentalTracker.onHeld(BOAT, raft(NOW + HOUR), NOW);
		assertEquals(BOAT, RentalTracker.entries().get(0).itemId);
	}

	@Test
	void anEmptyReturnMenuClearsEverything() {
		RentalTracker.onHeld(BOAT, raft(NOW + HOUR), NOW);
		RentalTracker.reconcile(List.of());
		assertTrue(RentalTracker.entries().isEmpty());
	}
}
