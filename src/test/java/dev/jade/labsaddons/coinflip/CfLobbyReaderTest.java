package dev.jade.labsaddons.coinflip;

import dev.jade.labsaddons.casino.SlotView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Slots and lore verbatim from coinflip.jsonl. */
class CfLobbyReaderTest {
	private static final long DOLLAR = 100L;
	private static final int SLOTS = 45;

	private static List<SlotView> lobby() {
		List<SlotView> slots = new ArrayList<>();
		for (int i = 0; i < SLOTS; i++) {
			slots.add(SlotView.empty(i));
		}
		slots.set(0, new SlotView(0, "Blackbill67 (#4566)", List.of(
				"Wager: $100,000,000",
				"Face: tails",
				"Created: 9/18/26, 11:52 PM",
				"",
				"Click to take this coinflip",
				"for $100,000,000"), 1));
		slots.set(1, new SlotView(1, "gaydo123 (#4581)", List.of(
				"Wager: $29,000,000",
				"Face: heads",
				"Created: 9/19/26, 6:42 AM",
				"",
				"Click to take this coinflip",
				"for $29,000,000"), 1));
		slots.set(2, new SlotView(2, "GonjaMayor (#4603)", List.of(
				"Wager: $750",
				"Face: heads",
				"Created: 9/19/26, 5:32 PM",
				"",
				"Click to take this coinflip",
				"for $750"), 1));
		slots.set(37, new SlotView(37, "Create a Coinflip",
				List.of("/cf create <wager> <heads/tails>"), 1));
		slots.set(38, new SlotView(38, "Coinflip Information", List.of(
				"Minimum Wager: $200",
				"Maximum Wager: $100,000,000",
				"Expiry Time: 24 hours",
				"Tax: 5%",
				"",
				"Commands: /cf help"), 1));
		slots.set(40, new SlotView(40, "Refresh Coinflips", List.of(), 1));
		return slots;
	}

	@Test
	void recognisesTheLobbyByTheBookAlone() {
		assertTrue(CfLobbyReader.isLobby(lobby()));
		assertTrue(CfLobbyReader.looksLikeLobby("Coinflip Information", "Refresh Coinflips"));
		assertFalse(CfLobbyReader.looksLikeLobby("Mines: 9", "Investment: $2,300"));
		assertTrue(CfLobbyReader.isLobbyTitle("Active Coinflips"));
		assertFalse(CfLobbyReader.isLobbyTitle("Flipping a coin..."));
	}

	@Test
	void readsEveryPostedFlipWithTheIdFromItsOwnName() {
		List<CfLobbyReader.Row> rows = CfLobbyReader.read(lobby()).rows();
		assertEquals(3, rows.size());
		CfLobbyReader.Row first = rows.get(0);
		assertEquals(0, first.slot());
		assertEquals(4566, first.id());
		assertEquals("Blackbill67", first.player());
		assertEquals(100_000_000 * DOLLAR, first.wagerCents());
		assertEquals("tails", first.creatorFace());
		assertEquals("9/18/26, 11:52 PM", first.created());
		assertEquals(750 * DOLLAR, rows.get(2).wagerCents());
		assertEquals(4603, rows.get(2).id());
	}

	@Test
	void youTakeWhicheverSideTheyDidNot() {
		List<CfLobbyReader.Row> rows = CfLobbyReader.read(lobby()).rows();
		assertEquals("heads", rows.get(0).yourFace());
		assertEquals("tails", rows.get(1).yourFace());
	}

	@Test
	void aFlipWithNoStatedSideClaimsNeither() {
		List<SlotView> slots = lobby();
		slots.set(0, new SlotView(0, "Blackbill67 (#4566)",
				List.of("Wager: $100,000,000"), 1));
		assertEquals("", CfLobbyReader.read(slots).rows().get(0).yourFace());
	}

	@Test
	void readsTheBoundsOffTheBook() {
		CfLobbyReader.Lobby read = CfLobbyReader.read(lobby());
		assertEquals(200 * DOLLAR, read.minCents());
		assertEquals(100_000_000 * DOLLAR, read.maxCents());
		assertEquals("24 hours", read.expiry());
	}

	@Test
	void theDecorationIsNotMistakenForAFlip() {
		// Slots 36-44 are the footer: an info head, an emerald, a book and an eye. None of
		// them is named "Somebody (#1234)", so none of them becomes a wager on screen.
		for (CfLobbyReader.Row row : CfLobbyReader.read(lobby()).rows()) {
			assertTrue(row.slot() < CfLobbyReader.FLIP_SLOTS);
			assertTrue(row.wagerCents() > 0L);
		}
	}

	@Test
	void anEmptyLobbyReadsAsEmptyRatherThanUnparsed() {
		List<SlotView> slots = lobby();
		for (int i = 0; i < CfLobbyReader.FLIP_SLOTS; i++) {
			slots.set(i, SlotView.empty(i));
		}
		assertTrue(CfLobbyReader.isLobby(slots));
		assertTrue(CfLobbyReader.read(slots).rows().isEmpty());
	}

	/**
	 * This parse runs inside the board's draw, so a head the server has put an absurd id
	 * on used to throw on every frame it was on screen rather than once.
	 */
	@Test
	void aHeadWithAnAbsurdIdIsSkippedRatherThanThrown() {
		List<SlotView> slots = lobby();
		slots.set(2, new SlotView(2, "Mallory (#99999999999)", List.of(
				"Wager: $200",
				"Face: heads"), 1));
		List<CfLobbyReader.Row> rows = CfLobbyReader.read(slots).rows();
		assertTrue(rows.stream().noneMatch(row -> row.player().equals("Mallory")));
		// The rest of the menu still reads, which is the point of skipping the row.
		assertFalse(rows.isEmpty());
	}
}
