package dev.jade.labsaddons.rental;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class RentalLoreTest {
	private static long eastern(int month, int day, int hour, int minute) {
		return ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, RentalLore.SERVER_ZONE)
				.toInstant().toEpochMilli();
	}

	// Lore of the Portable Raft as it sat in the inventory, flattened.
	private static final List<String> HELD = List.of(
			"Right-click to spawn a boat.",
			"",
			"* RENTED ITEM",
			"Rented from: Zemperus",
			"Rented to: Ophiliah",
			"9/23/26, 1:01 AM - 9/23/26, 3:01 AM.");

	@Test
	public void readsTheEndOfTheWindowOffTheItem() {
		RentalLore.Rental r = RentalLore.fromItem("Portable Raft", HELD);
		assertNotNull(r);
		assertEquals("Zemperus", r.owner());
		assertEquals(eastern(9, 23, 3, 1), r.endMs());
	}

	@Test
	public void readsTheReturnMenuExpiry() {
		RentalLore.Rental r = RentalLore.fromReturnMenu("Portable Raft", List.of(
				"Right-click to spawn a boat.", "-------------------", "",
				"Rented from: Zemperus", "Expiry: 9/23/26, 4:01 AM (02h:49m)", "",
				"Left-click to return.", "Right-click to extend rent."));
		assertNotNull(r);
		assertEquals(eastern(9, 23, 4, 1), r.endMs());
	}

	@Test
	public void theShopListingIsNotARental() {
		assertNull(RentalLore.fromItem("Portable Raft", List.of(
				"Right-click to spawn a boat.", " * Poster: Zemperus (9/20/26, 4:19 PM)",
				" * Rate: $10,000/hour", " * Rental Window: 1-10 hours", "Click to rent.")));
		// Each reader only takes its own time line.
		assertNull(RentalLore.fromReturnMenu("Portable Raft", HELD));
	}

	@Test
	public void parsesTheReturnMessage() {
		RentalLore.Returned r = RentalLore.returned(
				"Item Rental » You have returned the rented Portable Raft to Zemperus. Thank you!");
		assertNotNull(r);
		assertEquals("Portable Raft", r.name());
		assertEquals("Zemperus", r.owner());
	}

	@Test
	public void parsesTheExtendMessageAsTimeLeft() {
		RentalLore.Extension e = RentalLore.extended("Item Rental » You have paid $10,000 to extend your rent"
				+ " of Portable Raft from Zemperus for another 1 hour. Your rental now ends in 02h:50m."
				+ " Return the item by the end of your rental time with /rent return.");
		assertNotNull(e);
		assertEquals("Portable Raft", e.name());
		assertEquals("Zemperus", e.owner());
		assertEquals((2 * 60 + 50) * 60_000L, e.remainingMs());
	}

	@Test
	public void extendKeepsTheDayPart() {
		RentalLore.Extension e = RentalLore.extended("Item Rental » You have paid $10,000 to extend your rent"
				+ " of Portable Raft from Zemperus for another 1 hour. Your rental now ends in 1d:02h:50m."
				+ " Return the item by the end of your rental time with /rent return.");
		assertNotNull(e);
		assertEquals(((24 + 2) * 60 + 50) * 60_000L, e.remainingMs());
	}

	@Test
	public void theRentMessageIsNeitherReturnNorExtend() {
		String paid = "Item Rental » You have paid $20,000 to rent Portable Raft from Zemperus for 2 hours"
				+ " (contract #3084). Return the item by the end of your rental time with /rent return.";
		assertNull(RentalLore.returned(paid));
		assertNull(RentalLore.extended(paid));
	}

	private static List<String> window(String stamp) {
		return List.of("* RENTED ITEM", "Rented from: Zemperus", stamp);
	}

	@Test
	public void extendAcceptsEveryDurationShape() {
		String line = "Item Rental » You have paid $10,000 to extend your rent of Portable Raft from Zemperus"
				+ " for another 1 hour. Your rental now ends in %s. Return the item.";
		assertEquals(59 * 60_000L, RentalLore.extended(line.formatted("59m")).remainingMs());
		assertEquals(60 * 60_000L, RentalLore.extended(line.formatted("01h:00m")).remainingMs());
		assertEquals(((10 * 24 + 1) * 60 + 5) * 60_000L, RentalLore.extended(line.formatted("10d:01h:05m")).remainingMs());
	}

	@Test
	public void extendWithAZeroTimeLeftIsNotAnExtension() {
		assertNull(RentalLore.extended("Item Rental » You have paid $10,000 to extend your rent of Portable Raft"
				+ " from Zemperus for another 1 hour. Your rental now ends in 00m. Return the item."));
	}

	@Test
	public void returnKeepsAMultiWordItemName() {
		RentalLore.Returned r = RentalLore.returned(
				"Item Rental » You have returned the rented Speed Boots of Haste to Alex. Thank you!");
		assertEquals("Speed Boots of Haste", r.name());
		assertEquals("Alex", r.owner());
	}

	@Test
	public void theWindowReadsNoonAndMidnightCorrectly() {
		assertEquals(eastern(9, 23, 0, 5),
				RentalLore.fromItem("Portable Raft", window("9/22/26, 10:05 PM - 9/23/26, 12:05 AM.")).endMs());
		assertEquals(eastern(9, 23, 12, 30),
				RentalLore.fromItem("Portable Raft", window("9/23/26, 10:30 AM - 9/23/26, 12:30 PM.")).endMs());
	}

	@Test
	public void theWindowReadsWithNarrowNoBreakSpaces() {
		RentalLore.Rental r = RentalLore.fromItem("Portable Raft",
				window("9/23/26, 1:01\u202fAM - 9/23/26, 3:01\u202fAM."));
		assertEquals(eastern(9, 23, 3, 1), r.endMs());
		RentalLore.Rental nb = RentalLore.fromItem("Portable Raft",
				window("9/23/26, 1:01\u00a0AM - 9/23/26, 3:01\u00a0AM."));
		assertEquals(eastern(9, 23, 3, 1), nb.endMs());
	}

	@Test
	public void theServerClockIsEasternSoAnExpiryAcrossTheFallBackReadsRight() {
		// 11/1/26 3:00 AM is after the 2 AM change, so it is EST (UTC-5).
		assertEquals(java.time.Instant.parse("2026-11-01T08:00:00Z").toEpochMilli(),
				RentalLore.fromItem("Portable Raft", window("11/1/26, 1:00 AM - 11/1/26, 3:00 AM.")).endMs());
	}

	@Test
	public void aRentalWithoutAnOwnerOrATimeOrNameIsNotRead() {
		assertNull(RentalLore.fromItem("Portable Raft", List.of("9/23/26, 1:01 AM - 9/23/26, 3:01 AM.")));
		assertNull(RentalLore.fromItem("Portable Raft", List.of("Rented from: Zemperus")));
		assertNull(RentalLore.fromItem(" ", HELD));
	}

	@Test
	public void anUnparseableStampIsNotRead() {
		assertNull(RentalLore.fromItem("Portable Raft", window("13/45/26, 1:01 AM - 13/45/26, 3:01 AM.")));
	}
}
