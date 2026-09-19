package dev.jade.labsaddons.mines;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every line below is verbatim from the capture. */
class MinesChatTest {
	@BeforeEach
	void clear() {
		MinesChat.reset();
	}

	@Test
	void readsAWin() {
		MinesChat.onMessage("Mines » You won $10,062.5!");
		assertTrue(MinesChat.lastOutcome().won());
		assertEquals(1_006_250L, MinesChat.lastOutcome().amountCents());
	}

	@Test
	void readsALoss() {
		MinesChat.onMessage("Mines » You have lost $3,500!");
		assertFalse(MinesChat.lastOutcome().won());
		assertEquals(350_000L, MinesChat.lastOutcome().amountCents());
	}

	@Test
	void aNewGameRetiresTheLastResult() {
		MinesChat.onMessage("Mines » You won $10,062.5!");
		MinesChat.onMessage("Mines » Starting a new game, your $3,500 investment has been taken.");
		assertNull(MinesChat.lastOutcome());
	}

	@Test
	void ignoresEverythingElse() {
		// Another player's minigame win landed in the middle of the captured round.
		MinesChat.onMessage("» Tanges typed the message in 17.805 seconds and won $7,500!");
		MinesChat.onMessage("MCLabs » $10,062.5 has been added to your account.");
		MinesChat.onMessage(null);
		assertNull(MinesChat.lastOutcome());
	}
}
