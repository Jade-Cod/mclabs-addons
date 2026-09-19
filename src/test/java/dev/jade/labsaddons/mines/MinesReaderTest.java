package dev.jade.labsaddons.mines;

import dev.jade.labsaddons.casino.CasinoPanel;
import dev.jade.labsaddons.casino.SlotView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Built from the Mines rounds captured on 2026-09-18 — every name and lore line below is
 * what the server actually sent, so a server-side wording change fails here rather than
 * silently leaving the board blank.
 */
class MinesReaderTest {
	private static List<SlotView> blank() {
		List<SlotView> slots = new ArrayList<>();
		for (int i = 0; i < CasinoPanel.CONTAINER_SLOTS; i++) {
			slots.add(SlotView.empty(i));
		}
		return slots;
	}

	private static void set(List<SlotView> slots, int index, String name, String... lore) {
		slots.set(index, new SlotView(index, name, List.of(lore), 1));
	}

	/** A fresh grid: the mine count, the stake, and 25 face-down tiles. */
	private static List<SlotView> fresh() {
		List<SlotView> slots = blank();
		set(slots, MinesReader.MINE_COUNT_SLOT, "Mines: 9");
		set(slots, MinesReader.STAKE_SLOT, "Investment: $3,500");
		for (int slot : MinesReader.TILE_SLOTS) {
			set(slots, slot, "???", "", "Click to reveal!");
		}
		return slots;
	}

	/** The captured win: three stars, cashed out at $10,062.50. */
	private static List<SlotView> threeStars() {
		List<SlotView> slots = fresh();
		set(slots, 11, "Safe");
		set(slots, 13, "Safe");
		set(slots, 30, "Safe");
		set(slots, MinesReader.CASH_OUT_SLOT, "Cash Out",
				"", "Click to cash out", "for $10,062.5",
				"", "Or reveal another", "star for $17,028.46");
		return slots;
	}

	@Test
	void recognisesTheMenuByItsContents() {
		assertTrue(MinesReader.isMines(fresh()));
		assertTrue(MinesReader.isMines(threeStars()));
		assertFalse(MinesReader.isMines(blank()));
		assertFalse(MinesReader.isMines(List.of()));
	}

	@Test
	void refusesAGridWithoutTiles() {
		// The two figures alone are not the game: something else could state them.
		List<SlotView> slots = blank();
		set(slots, MinesReader.MINE_COUNT_SLOT, "Mines: 9");
		set(slots, MinesReader.STAKE_SLOT, "Investment: $3,500");
		assertFalse(MinesReader.isMines(slots));
	}

	@Test
	void readsTheStakeAndMineCount() {
		MinesState state = MinesReader.read(fresh());
		assertEquals(350_000L, state.stakeCents());
		assertEquals(9, state.mines());
		assertEquals(0, state.stars());
		assertFalse(state.blown());
		assertFalse(state.canCashOut());
	}

	@Test
	void readsTheGridInReadingOrder() {
		// Row 1 columns 0 and 2, then row 3 column 1 — slots 11, 13 and 30.
		MinesState state = MinesReader.read(threeStars());
		assertEquals(3, state.stars());
		assertEquals(MinesState.Tile.SAFE, state.tiles().get(5));
		assertEquals(MinesState.Tile.SAFE, state.tiles().get(7));
		assertEquals(MinesState.Tile.SAFE, state.tiles().get(16));
		assertEquals(MinesState.Tile.HIDDEN, state.tiles().get(0));
	}

	@Test
	void readsBothCashOutFigures() {
		MinesState state = MinesReader.read(threeStars());
		assertTrue(state.canCashOut());
		assertEquals(1_006_250L, state.cashOutCents());
		assertEquals(1_702_846L, state.nextCents());
		// The server's own figures win over anything derived.
		assertEquals(1_006_250L, state.rungCents(3));
		assertEquals(1_702_846L, state.rungCents(4));
		assertFalse(state.rungIsDerived(3));
		assertFalse(state.rungIsDerived(4));
		// A rung it never stated has to be worked out.
		assertTrue(state.rungIsDerived(5));
	}

	@Test
	void seesTheMineThatEndsTheGame() {
		List<SlotView> slots = threeStars();
		set(slots, 32, "Mine");
		// The server takes the Cash Out item away the moment you lose.
		set(slots, MinesReader.CASH_OUT_SLOT, "");
		MinesState state = MinesReader.read(slots);
		assertTrue(state.blown());
		assertFalse(state.canCashOut());
	}

	@Test
	void treatsAnUnreadableTileAsFaceDown() {
		// Mid-update and mid-click-prediction both look like this, and neither is a
		// reveal — guessing either way would paint a tile the server never turned.
		List<SlotView> slots = fresh();
		set(slots, 22, "");
		MinesState state = MinesReader.read(slots);
		assertEquals(MinesState.Tile.HIDDEN, state.tiles().get(12));
		assertEquals(0, state.stars());
	}

	@Test
	void mapsGridCellsOntoTheSlotsTheServerUses() {
		assertEquals(2, MinesReader.tileSlot(0, 0));
		assertEquals(6, MinesReader.tileSlot(0, 4));
		assertEquals(11, MinesReader.tileSlot(1, 0));
		assertEquals(42, MinesReader.tileSlot(4, 4));
	}
}
