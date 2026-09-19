package dev.jade.labsaddons.blackjack;

import dev.jade.labsaddons.casino.CasinoPanel;
import dev.jade.labsaddons.casino.SlotView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Built from the four BondJoules hands captured on 2026-09-18. Every name, lore line and
 * window title below is what the server actually sent.
 */
class BjReaderTest {
	private static final String ALEMBIC = "⚗";
	private static final String BOLT = "⚡";
	private static final String FLAME = "🔥";
	private static final String ACE = "Δ";
	private static final String YOUR_TURN = "Energize or Finalize?";

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

	private static void hand(List<SlotView> slots, int index, String name, int total,
			String... cards) {
		slots.set(index, new SlotView(index, name, List.of(cards), total));
	}

	/** Both seats and a status band: the shape of every frame of the game. */
	private static List<SlotView> table(String banner) {
		List<SlotView> slots = blank();
		set(slots, BjReader.LAB_SEAT_SLOT, "Competing Lab");
		set(slots, BjReader.YOUR_SEAT_SLOT, "You");
		for (int slot = 18; slot <= 35; slot++) {
			set(slots, slot, banner);
		}
		return slots;
	}

	/** The captured round 1 at the decision point: 6 of clubs and 10 of hearts against a 10. */
	private static List<SlotView> yourTurn() {
		List<SlotView> slots = table(YOUR_TURN);
		hand(slots, BjReader.LAB_HAND_SLOT, "Competing Lab's Experiment", 10,
				"10" + BOLT, "??");
		hand(slots, BjReader.YOUR_HAND_SLOT, "Your Experiment", 16,
				"6" + FLAME, "10" + ALEMBIC);
		set(slots, BjReader.STAND_SLOT, "Finalize", "Click to finalize", "at 16J.");
		set(slots, BjReader.HIT_SLOT, "Energize", "Click to add more", "energy.");
		return slots;
	}

	@Test
	void recognisesTheMenuByItsSeats() {
		assertTrue(BjReader.isBlackjack(yourTurn()));
		assertTrue(BjReader.isBlackjack(table("Starting experiment...")));
		assertFalse(BjReader.isBlackjack(blank()));
		assertFalse(BjReader.isBlackjack(List.of()));
	}

	@Test
	void refusesATableWithNoStatusBand() {
		// Every other reading keys off the band, so a container without one is not yet
		// worth drawing.
		List<SlotView> slots = blank();
		set(slots, BjReader.LAB_SEAT_SLOT, "Competing Lab");
		set(slots, BjReader.YOUR_SEAT_SLOT, "You");
		assertFalse(BjReader.isBlackjack(slots));
	}

	@Test
	void takesTheStakeAndBothTotalsFromTheTitle() {
		BjState state = BjReader.read(yourTurn(), "BondJoules ($3,100) [16 - 10]");
		assertEquals(310_000L, state.stakeCents());
		assertEquals(16, state.yourTotal());
		assertEquals(10, state.labTotal());
	}

	@Test
	void fallsBackToTheStackCountBeforeTheDealStarts() {
		// The bracket is absent until the first card lands, but the hand item's count is
		// the total too.
		BjState state = BjReader.read(yourTurn(), "BondJoules ($3,100)");
		assertEquals(310_000L, state.stakeCents());
		assertEquals(16, state.yourTotal());
		assertEquals(10, state.labTotal());
	}

	@Test
	void readsBothHandsFromOneItemEach() {
		BjState state = BjReader.read(yourTurn(), "BondJoules ($3,100) [16 - 10]");
		assertEquals(2, state.yourHand().size());
		assertEquals(6, state.yourHand().get(0).rank());
		assertEquals(Card.Suit.CLUBS, state.yourHand().get(0).suit());
		assertEquals(Card.Suit.HEARTS, state.yourHand().get(1).suit());
		assertEquals(2, state.labHand().size());
		assertTrue(state.labHand().get(1).isHidden());
	}

	@Test
	void readsTheControlsTheServerIsOffering() {
		BjState state = BjReader.read(yourTurn(), "BondJoules ($3,100) [16 - 10]");
		assertTrue(state.canHit());
		assertTrue(state.canStand());
		// Double Down was not offered on this hand; it appeared on the captured round 4.
		assertFalse(state.canDouble());

		List<SlotView> withDouble = yourTurn();
		set(withDouble, BjReader.DOUBLE_SLOT, "Double Down",
				"Double your investment to $6,200,", "but you will only be able to",
				"add energy one more time.");
		assertTrue(BjReader.read(withDouble, "BondJoules ($3,100) [10 - 7]").canDouble());
	}

	@Test
	void readsEveryPhaseTheBandStates() {
		assertEquals(BjState.Phase.DEALING, BjReader.phase("Starting experiment..."));
		assertEquals(BjState.Phase.YOUR_TURN, BjReader.phase(YOUR_TURN));
		assertEquals(BjState.Phase.LAB_TURN,
				BjReader.phase("Competing lab is experimenting..."));
		assertEquals(BjState.Phase.WON, BjReader.phase("Perfect Reaction! You win $7,750!"));
		assertEquals(BjState.Phase.PUSH, BjReader.phase("Neutralized!"));
		assertEquals(BjState.Phase.LOST, BjReader.phase("Your experiment lost!"));
		assertEquals(BjState.Phase.DEALING, BjReader.phase(""));
	}

	@Test
	void knowsWhenTheHandIsOver() {
		assertTrue(BjReader.read(table("Your experiment lost!"), "BondJoules ($3,100) [16 - 21]")
				.settled());
		assertFalse(BjReader.read(yourTurn(), "BondJoules ($3,100) [16 - 10]").settled());
	}

	@Test
	void spotsTheSoftAceFromTheCapturedWin() {
		List<SlotView> slots = table(YOUR_TURN);
		hand(slots, BjReader.YOUR_HAND_SLOT, "Your Experiment", 11, ACE + ALEMBIC);
		BjState state = BjReader.read(slots, "BondJoules ($3,100) [11 - 2]");
		assertEquals(11, state.yourTotal());
		assertTrue(state.soft());
		assertEquals("0%", state.bustText());
	}
}
