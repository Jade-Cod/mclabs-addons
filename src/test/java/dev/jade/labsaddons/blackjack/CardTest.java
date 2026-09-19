package dev.jade.labsaddons.blackjack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every one of the fifteen distinct tokens the capture contained, written as escapes so
 * the test says which code point it means rather than relying on this file's encoding.
 */
class CardTest {
	private static final String RADIOACTIVE = "☢";
	private static final String ALEMBIC = "⚗";
	private static final String BOLT = "⚡";
	/** Outside the basic plane: a surrogate pair, which is why parsing reads code points. */
	private static final String FLAME = "🔥";
	private static final String ACE = "Δ";

	@Test
	void mapsTheFourLabSymbolsOntoSuits() {
		assertEquals(Card.Suit.DIAMONDS, Card.parse("8" + RADIOACTIVE).suit());
		assertEquals(Card.Suit.HEARTS, Card.parse("3" + ALEMBIC).suit());
		assertEquals(Card.Suit.SPADES, Card.parse("2" + BOLT).suit());
		assertEquals(Card.Suit.CLUBS, Card.parse("4" + FLAME).suit());
	}

	@Test
	void readsTheFlameDespiteItsSurrogatePair() {
		Card card = Card.parse("6" + FLAME);
		assertEquals(6, card.rank());
		assertEquals(Card.Suit.CLUBS, card.suit());
		assertEquals("6", card.rankText());
	}

	@Test
	void deltaIsTheAce() {
		Card card = Card.parse(ACE + ALEMBIC);
		assertTrue(card.isAce());
		// It counted eleven in the window title both times it appeared.
		assertEquals(11, card.rank());
		assertEquals("A", card.rankText());
	}

	@Test
	void readsEveryTenValueCardAsATen() {
		// Jacks, queens and kings are indistinguishable in the data — all four suits of
		// "10" turned up, so this is a 52-card deck rendering its faces as tens.
		for (String suit : new String[]{RADIOACTIVE, ALEMBIC, BOLT, FLAME}) {
			assertEquals(10, Card.parse("10" + suit).rank());
		}
	}

	@Test
	void theHoleCardIsFaceDown() {
		Card card = Card.parse("??");
		assertTrue(card.isHidden());
		assertNull(card.suit());
		assertEquals("?", card.rankText());
	}

	@Test
	void redAndBlackSplitTwoAndTwo() {
		assertTrue(Card.Suit.DIAMONDS.isRed());
		assertTrue(Card.Suit.HEARTS.isRed());
		assertFalse(Card.Suit.SPADES.isRed());
		assertFalse(Card.Suit.CLUBS.isRed());
	}

	@Test
	void refusesWhatIsNotACard() {
		// The server pads a hand's lore with blank lines.
		assertNull(Card.parse(""));
		assertNull(Card.parse("   "));
		assertNull(Card.parse(null));
		assertNull(Card.parse("Your Experiment"));
		// A rank with no symbol, and a symbol this mapping does not know.
		assertNull(Card.parse("7"));
		assertNull(Card.parse("7x"));
		// Ranks outside the deck.
		assertNull(Card.parse("1" + BOLT));
		assertNull(Card.parse("11" + BOLT));
	}
}
