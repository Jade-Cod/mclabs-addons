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
		// Sampled across a minute of holding, because the coin rocks while it waits and a
		// single instant would not catch a rock that carried it over the edge.
		for (long held = 0; held <= 60_000; held += 37) {
			float late = CfToss.halfTurns(CfToss.HOLD_MS + held);
			assertTrue(late >= CfToss.HOLD_AT - 0.05f, "it should not turn back: " + late);
			assertTrue(late < CfToss.HOLD_AT + 0.5f,
					"it must stop short of the flip, not complete it: " + late);
			assertEquals(CfToss.faceUp(CfToss.HOLD_AT), CfToss.faceUp(late),
					"the rock must never change which face is up");
		}
	}

	@Test
	void itRocksWhileItWaitsRatherThanStandingStill() {
		// A coin frozen on its rim reads as a frozen screen.
		float low = 1f;
		float high = 0f;
		for (long held = 0; held <= 2_000; held += 20) {
			float squeeze = CfToss.squeeze(CfToss.halfTurns(CfToss.HOLD_MS + held));
			low = Math.min(low, squeeze);
			high = Math.max(high, squeeze);
		}
		assertTrue(high - low > 0.05f, "the rock should be visible: " + (high - low));
	}

	@Test
	void itSquashesOnImpactAndSettlesFlat() {
		assertTrue(CfToss.settle(0) < 0.85f, "flattened as it hits: " + CfToss.settle(0));
		float peak = 0f;
		for (long ms = 0; ms <= 400; ms += 5) {
			peak = Math.max(peak, CfToss.settle(ms));
		}
		assertTrue(peak > 1.05f, "it should overshoot once: " + peak);
		assertEquals(1f, CfToss.settle(2_000), 0.01f, "and then be still");
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
