package dev.jade.labsaddons.carnage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class CarnageKillPairingTest {

	@Test
	public void aKillIsCreditedOnceItsAwardArrives() {
		CarnageKillPairing pairing = new CarnageKillPairing();
		pairing.onDeath("Geist", 1_000);
		assertTrue(pairing.drain(1_100).isEmpty());
		pairing.onAward(1_050);
		assertTrue(pairing.drain(1_200).isEmpty(), "waits a beat for a nearer death");
		assertEquals(List.of("Geist"), pairing.drain(1_600));
	}

	@Test
	public void aDeathNobodyPaidUsForIsDropped() {
		// tagged it, someone else finished it
		CarnageKillPairing pairing = new CarnageKillPairing();
		pairing.onDeath("Slimer", 1_000);
		assertTrue(drainUntil(pairing, 5_000).isEmpty());
	}

	@Test
	public void aStolenKillDoesNotTakeOurCredit() {
		CarnageKillPairing pairing = new CarnageKillPairing();
		pairing.onDeath("Slimer", 1_000);
		pairing.onDeath("Geist", 1_900);
		pairing.onAward(1_900);
		assertEquals(List.of("Geist"), drainUntil(pairing, 5_000));
	}

	@Test
	public void theNamedDeathOfAMultiEntityMobWins() {
		CarnageKillPairing pairing = new CarnageKillPairing();
		pairing.onAward(1_000);
		pairing.onDeath(CarnageKillPairing.UNNAMED, 1_000); // the hitbox we hit
		pairing.onDeath("Scarecrow", 1_400);
		assertEquals(List.of("Scarecrow"), drainUntil(pairing, 5_000));
	}

	@Test
	public void aLeftoverHitboxDoesNotTakeTheNextKill() {
		CarnageKillPairing pairing = new CarnageKillPairing();
		pairing.onAward(1_000);
		pairing.onDeath("Pumpkin Fiend", 1_000);
		pairing.onDeath("Pumpkin Fiend", 1_700); // its spider, removed a beat later
		pairing.onAward(2_200);
		pairing.onDeath("Geist", 2_250);
		assertEquals(List.of("Pumpkin Fiend", "Geist"), drainUntil(pairing, 6_000));
	}

	@Test
	public void severalKillsInOneTickAreEachCredited() {
		CarnageKillPairing pairing = new CarnageKillPairing();
		for (int i = 0; i < 3; i++) {
			pairing.onDeath("Spiderling", 1_000);
			pairing.onAward(1_000);
		}
		assertEquals(List.of("Spiderling", "Spiderling", "Spiderling"), drainUntil(pairing, 5_000));
	}

	/** Drains a tick at a time, the way the tracker does. */
	private static List<String> drainUntil(CarnageKillPairing pairing, long untilMs) {
		List<String> kills = new ArrayList<>();
		for (long t = 0; t <= untilMs; t += 50) {
			kills.addAll(pairing.drain(t));
		}
		return kills;
	}
}
