package dev.jade.labsaddons.blackjack;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BjOddsTest {
	private static final String ALEMBIC = "⚗";
	private static final String BOLT = "⚡";
	private static final String FLAME = "🔥";
	private static final String ACE = "Δ";

	@Test
	void bustChanceOnTheCapturedHand() {
		// 6 of clubs and 10 of hearts: anything above a five busts, which is 6-9 plus
		// every ten-value card — 32 of 52.
		assertEquals(32 / 52.0, BjOdds.bustChance(16, false), 1e-9);
		assertEquals("62%", BjOdds.bustText(16, false));
	}

	@Test
	void lowTotalsCannotBust() {
		assertEquals(0.0, BjOdds.bustChance(11, false), 1e-9);
		assertEquals(0.0, BjOdds.bustChance(4, false), 1e-9);
	}

	@Test
	void aSoftHandCannotBustBecauseItsAceDemotes() {
		assertEquals(0.0, BjOdds.bustChance(16, true), 1e-9);
		assertEquals("0%", BjOdds.bustText(16, true));
	}

	@Test
	void twentyIsAlmostAlwaysABust() {
		// Only the four aces survive, and each comes in as a one.
		assertEquals(48 / 52.0, BjOdds.bustChance(20, false), 1e-9);
		assertEquals("92%", BjOdds.bustText(20, false));
	}

	@Test
	void spotsASoftHand() {
		Card ace = Card.parse(ACE + ALEMBIC);
		assertTrue(BjOdds.isSoft(List.of(ace), 11));
		assertTrue(BjOdds.isSoft(List.of(ace, Card.parse("10" + BOLT)), 21));
		// Once the ace has been demoted the total no longer carries the extra ten.
		assertFalse(BjOdds.isSoft(List.of(ace, Card.parse("10" + BOLT),
				Card.parse("5" + ALEMBIC)), 16));
	}

	@Test
	void aHandWithNoAceIsHard() {
		assertFalse(BjOdds.isSoft(
				List.of(Card.parse("6" + FLAME), Card.parse("10" + ALEMBIC)), 16));
	}

	@Test
	void saysNothingWhereAFigureWouldBeMeaningless() {
		assertEquals("—", BjOdds.bustText(0, false));
		assertEquals("—", BjOdds.bustText(21, false));
		assertEquals("—", BjOdds.bustText(22, false));
	}

	@Test
	void anUnseenHoleCardIsNotASoftHand() {
		// The lab's hand always has one; guessing its total would be inventing data.
		assertFalse(BjOdds.isSoft(List.of(Card.parse("10" + BOLT), Card.HIDDEN), 10));
	}
}
