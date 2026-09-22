package dev.jade.labsaddons.casino;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Both betting screens as captured. They are nearly the same menu, and which game it is
 * has to fall out of what is there rather than out of the title.
 */
class BetReaderTest {
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

	/** The Mines screen at the stake and count the capture started from. */
	private static List<SlotView> minesScreen() {
		List<SlotView> slots = blank();
		set(slots, 13, "+1 Mine (Click)", "Increase number of mines.", "Mines: 3");
		set(slots, 19, "-$10,000 (Click)", "Decrease investment.", "Investment: $500");
		set(slots, 20, "-$1,000 (Click)", "Decrease investment.", "Investment: $500");
		set(slots, 21, "-$100 (Click)", "Decrease investment.", "Investment: $500");
		set(slots, 22, "Start Game", "Investment: $500", "Mines: 3");
		set(slots, 23, "+$100 (Click)", "Increase investment.", "Investment: $500");
		set(slots, 24, "+$1,000 (Click)", "Increase investment.", "Investment: $500");
		set(slots, 25, "+$10,000 (Click)", "Increase investment.", "Investment: $500");
		// The server's own copy-paste slip: the minus item says "increase" too.
		set(slots, 31, "-1 Mine (Click)", "Increase number of mines.", "Mines: 3");
		set(slots, 49, "Exit");
		return slots;
	}

	/** The BondJoules screen, which advertises shift-click and has no mine count. */
	private static List<SlotView> blackjackScreen() {
		List<SlotView> slots = blank();
		set(slots, 19, "-$10,000 (Click)", "Decrease investment.", "Investment: $100", "",
				"Shift-click for minimum investment.");
		set(slots, 20, "-$1,000 (Click)", "Decrease investment.", "Investment: $100");
		set(slots, 21, "-$100 (Click)", "Decrease investment.", "Investment: $100");
		set(slots, 22, "Start Experiment", "Investment: $100");
		set(slots, 23, "+$100 (Click)", "Increase investment.", "Investment: $100");
		set(slots, 24, "+$1,000 (Click)", "Increase investment.", "Investment: $100");
		set(slots, 25, "+$10,000 (Click)", "Increase investment.", "Investment: $100", "",
				"Shift-click for max investment.");
		set(slots, 40, "Exit");
		return slots;
	}

	@Test
	void recognisesBothScreens() {
		assertTrue(BetReader.isBet(minesScreen()));
		assertTrue(BetReader.isBet(blackjackScreen()));
		assertFalse(BetReader.isBet(blank()));
		assertFalse(BetReader.isBet(List.of()));
	}

	@Test
	void refusesAHalfSentStakeRow() {
		// Drawing dead chips would be worse than leaving the chest up for a frame.
		List<SlotView> slots = minesScreen();
		set(slots, 24, "");
		assertFalse(BetReader.isBet(slots));
	}

	@Test
	void readsTheMinesScreen() {
		BetReader.BetState state = BetReader.read(minesScreen());
		assertTrue(state.isMines());
		assertEquals(3, state.mines());
		assertEquals(50_000L, state.investmentCents());
		assertEquals("$500", state.investmentText());
		// Mines does not advertise shift-click, so the board does not offer it.
		assertFalse(state.hasMinMax());
		assertEquals(49, state.exitSlot());
	}

	@Test
	void readsTheBlackjackScreen() {
		BetReader.BetState state = BetReader.read(blackjackScreen());
		assertFalse(state.isMines());
		assertEquals(0, state.mines());
		assertEquals(10_000L, state.investmentCents());
		assertTrue(state.hasMinMax());
		assertEquals(40, state.exitSlot());
	}

	@Test
	void followsTheStakeUpTheCapturedSequence() {
		// $500 to $3,500 in three clicks of +$1,000, exactly as the capture went.
		for (long dollars : new long[]{1_500L, 2_500L, 3_500L}) {
			List<SlotView> slots = minesScreen();
			set(slots, 22, "Start Game", "Investment: $" + dollars, "Mines: 9");
			BetReader.BetState state = BetReader.read(slots);
			assertEquals(Money.fromDollars(dollars), state.investmentCents());
			assertEquals(9, state.mines());
		}
	}

	@Test
	void doesNotMistakeAnotherStartButtonForThisMenu() {
		assertFalse(BetReader.looksLikeBet("Start Game", "Gray Stained Glass Pane"));
		assertFalse(BetReader.looksLikeBet("Claim Rewards", "+$100 (Click)"));
		assertFalse(BetReader.looksLikeBet(null, null));
	}
}
