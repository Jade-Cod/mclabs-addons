package dev.jade.labsaddons.blackjack;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every line below is verbatim from the capture. */
class BjChatTest {
	@BeforeEach
	void clear() {
		BjChat.reset();
	}

	@Test
	void readsAWin() {
		BjChat.onMessage("BondJoules » You beat the competing lab and earned $7,750!");
		assertTrue(BjChat.lastOutcome().won());
		assertEquals(775_000L, BjChat.lastOutcome().amountCents());
	}

	@Test
	void readsALoss() {
		BjChat.onMessage("BondJoules » You have lost $3,100!");
		assertFalse(BjChat.lastOutcome().won());
		assertEquals(310_000L, BjChat.lastOutcome().amountCents());
	}

	@Test
	void readsAPush() {
		BjChat.onMessage("BondJoules » Neutralized! You have received your deposit back.");
		assertTrue(BjChat.lastOutcome().push());
		assertFalse(BjChat.lastOutcome().won());
	}

	@Test
	void readsTheRaisedStakeAfterADoubleDown() {
		// The only place the doubled figure is stated: the window title goes on showing
		// the original one.
		BjChat.onMessage("BondJoules » Doubling down! Your investment has been raised to $6,200!");
		assertEquals(620_000L, BjChat.doubledStakeCents());
		assertNull(BjChat.lastOutcome());
	}

	@Test
	void aNewHandRetiresTheLastResultAndTheDouble() {
		BjChat.onMessage("BondJoules » Doubling down! Your investment has been raised to $6,200!");
		BjChat.onMessage("BondJoules » You have lost $6,200!");
		BjChat.onMessage("BondJoules » Starting a new experiment, "
				+ "your $3,100 fund has been deposited.");
		assertNull(BjChat.lastOutcome());
		assertEquals(0L, BjChat.doubledStakeCents());
	}

	@Test
	void ignoresEverythingElse() {
		BjChat.onMessage("MCLabs » $7,750 has been added to your account.");
		BjChat.onMessage("Mines » You have lost $3,500!");
		BjChat.onMessage(null);
		assertNull(BjChat.lastOutcome());
	}
}
