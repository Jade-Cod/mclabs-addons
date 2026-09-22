package dev.jade.labsaddons.double2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The settle lines, verbatim from the captures. */
class D2ChatTest {
	@BeforeEach
	void clear() {
		D2Chat.reset();
	}

	@Test
	void readsTheWinLineDespiteItsLowerCaseCode() {
		D2Chat.onMessage("Double² » Your investment in cql profited you $1,940!");
		D2Chat.Outcome outcome = D2Chat.lastOutcome();
		assertEquals(Lab.CQL, outcome.lab());
		assertEquals(1940L, outcome.amount());
		assertTrue(outcome.won());
	}

	@Test
	void readsTheLossLine() {
		D2Chat.onMessage("Double² » Your investment in EOL lost you $100,000!");
		D2Chat.Outcome outcome = D2Chat.lastOutcome();
		assertEquals(Lab.EOL, outcome.lab());
		assertEquals(100000L, outcome.amount());
		assertFalse(outcome.won());
	}

	@Test
	void aNewRoundRetiresTheLastResult() {
		D2Chat.onMessage("Double² » Your investment in EOL lost you $100,000!");
		D2Chat.onMessage("Double² » Market now open (/double). Drawing in 60 seconds. "
				+ "Rounds since last EOL win: 35");
		assertNull(D2Chat.lastOutcome());
	}

	@Test
	void ignoresTheRoundsOtherChatter() {
		for (String line : new String[]{
				"Double² » You have invested $100,000 in EOL.",
				"Double² » Market closed. This round has 1 player investing a total of $100,000.",
				"Double² » Simulating the market...",
				"Double² » The profiting lab is ADL!",
				"Double² » You do not have enough money to invest $500,000.",
				"MCLabs » $1,940 has been added to your account.",
				null}) {
			D2Chat.onMessage(line);
		}
		assertNull(D2Chat.lastOutcome());
	}
}
