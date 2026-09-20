package dev.jade.labsaddons.coinflip;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CfTossTest {
	@Test
	void theCurveMatchesTheServersOwnCadence() {
		// 8 half-turns at 150 ms, 6 at 300, 4 at 450, 1 at 600 — read off the capture.
		assertEquals(0f, CfToss.halfTurns(0), 0.001f);
		assertEquals(8f, CfToss.halfTurns(1_200), 0.001f);
		assertEquals(14f, CfToss.halfTurns(3_000), 0.001f);
		assertEquals(18f, CfToss.halfTurns(4_800), 0.001f);
		assertEquals(CfToss.HOLD_AT, CfToss.halfTurns(CfToss.HOLD_MS), 0.001f);
	}

	@Test
	void theLastHalfTurnIsNeverCompletedOnTheClock() {
		// The whole point: the server has not said who won yet, so the coin may not land.
		float late = CfToss.halfTurns(CfToss.HOLD_MS + 60_000);
		assertTrue(late > CfToss.HOLD_AT, "it should creep past the hold");
		assertTrue(late < CfToss.HOLD_AT + 0.5f,
				"it must stop short of the flip, not complete it: " + late);
	}

	@Test
	void theCoinPassesEdgeOnHalfWayThroughEachTurn() {
		assertEquals(1f, CfToss.squeeze(0f), 0.001f);
		assertEquals(0f, CfToss.squeeze(0.5f), 0.001f);
		assertEquals(1f, CfToss.squeeze(1f), 0.001f);
		assertEquals(0f, CfToss.squeeze(19.5f), 0.001f);
	}

	@Test
	void eachHalfTurnShowsTheOtherFace() {
		assertEquals(0, CfToss.faceUp(0f));
		assertEquals(1, CfToss.faceUp(0.9f));
		assertEquals(1, CfToss.faceUp(1f));
		assertEquals(0, CfToss.faceUp(2f));
		assertEquals(1, CfToss.faceUp(19f));
	}

	@Test
	void itLandsOnTheWinnersFaceWithAtLeastHalfATurnToGo() {
		float held = CfToss.halfTurns(CfToss.HOLD_MS + 2_000);
		for (int face : new int[] {0, 1}) {
			float target = CfToss.landingTarget(held, face);
			assertEquals(target, Math.rint(target), 0.0001f, "it must settle face-on");
			assertEquals(face, CfToss.faceUp(target), "it must settle on the winner");
			assertTrue(target - held >= 0.5f, "the fall has to be visible");
			assertEquals(1f, CfToss.squeeze(target), 0.001f);
		}
	}

	@Test
	void theCoinIsOnTheTableAtBothEndsOfTheToss() {
		assertEquals(0f, CfToss.lift(0), 0.001f);
		assertEquals(1f, CfToss.lift(CfToss.HOLD_MS / 2), 0.001f);
		assertEquals(0f, CfToss.lift(CfToss.HOLD_MS), 0.001f);
	}
}
