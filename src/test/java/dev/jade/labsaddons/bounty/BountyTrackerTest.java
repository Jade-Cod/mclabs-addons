package dev.jade.labsaddons.bounty;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lines are from scenarios/bounty-hunt.jsonl, plus the join reminder seen in local captures. */
class BountyTrackerTest {
	private static final String START = "Bounty » Bounty Hunt has started! 6 chests each with 27 stacks of Glorootinide"
			+ " have been hidden around Spawn! Find them to get the chems. Use /bounty track in Spawn to track them.";
	private static final String REMINDER = "Bounty » Bounty Hunt active! 6 chests each containing 27 stacks of Pumpwartinide"
			+ " are hidden around Spawn. Find them to get the chems. Use /bounty track in Spawn to track them.";
	private static final String FOUND = "Bounty » Casper_Mat has found a bounty chest near the Purple Neighbourhood in 27:14!"
			+ " There are 5 bounty chests left hidden in Spawn. Use /bounty track in Spawn to track one.";
	private static final String LAST = "Bounty » Yeomans506 has found the last bounty chest near the Yellow Neighbourhood in 01:54!";
	private static final String ENDED = "Bounty » All bounty chests have been found! Bounty hunt has ended.";

	@BeforeEach
	void reset() {
		BountyTracker.clear();
	}

	@Test
	void startLineSeedsChemAndCount() {
		BountyTracker.onMessage(START);
		assertTrue(BountyTracker.isActive());
		assertEquals("Glorootinide", BountyTracker.chem());
		assertEquals(6, BountyTracker.remaining());
	}

	@Test
	void joinReminderSeedsTheSameState() {
		BountyTracker.onMessage(REMINDER);
		assertTrue(BountyTracker.isActive());
		assertEquals("Pumpwartinide", BountyTracker.chem());
		assertEquals(6, BountyTracker.remaining());
	}

	@Test
	void chestsLeftLineIsTheAuthoritativeCount() {
		BountyTracker.onMessage(START);
		BountyTracker.onMessage(FOUND);
		assertEquals(5, BountyTracker.remaining());
		assertEquals("Glorootinide", BountyTracker.chem());
		BountyTracker.onMessage("Bounty » Someone has found a bounty chest in 03:00! There are 2 bounty chests left hidden in Spawn.");
		assertEquals(2, BountyTracker.remaining());
	}

	@Test
	void missedFindsSelfHealOnTheNextCount() {
		BountyTracker.onMessage(START);
		BountyTracker.onMessage("There are 3 bounty chests left hidden in Spawn.");
		assertEquals(3, BountyTracker.remaining());
	}

	@Test
	void oneChestLeftIsParsedAsOne() {
		BountyTracker.onMessage(START);
		BountyTracker.onMessage("Bounty » Someone has found a bounty chest in 03:00! There is one bounty chest left hidden in Spawn.");
		assertEquals(1, BountyTracker.remaining());
		assertTrue(BountyTracker.isActive());
	}

	@Test
	void countLineWithoutAStartStillActivates() {
		BountyTracker.onMessage(FOUND);
		assertTrue(BountyTracker.isActive());
		assertEquals(5, BountyTracker.remaining());
		assertEquals("", BountyTracker.chem());
	}

	@Test
	void lastChestLineEndsTheHuntButKeepsTheChemName() {
		BountyTracker.onMessage(START);
		BountyTracker.onMessage(LAST);
		assertFalse(BountyTracker.isActive());
		assertEquals(0, BountyTracker.remaining());
		assertEquals("Glorootinide", BountyTracker.chem());
	}

	@Test
	void allFoundLineClearsEverything() {
		BountyTracker.onMessage(START);
		BountyTracker.onMessage(ENDED);
		assertFalse(BountyTracker.isActive());
		assertEquals("", BountyTracker.chem());
		assertEquals(0, BountyTracker.remaining());
	}

	@Test
	void clearResetsAnActiveHunt() {
		BountyTracker.onMessage(START);
		BountyTracker.clear();
		assertFalse(BountyTracker.isActive());
		assertEquals("", BountyTracker.chem());
		assertEquals(0, BountyTracker.remaining());
	}

	@Test
	void aNewStartReplacesTheOldChem() {
		BountyTracker.onMessage(START);
		BountyTracker.onMessage(REMINDER);
		assertEquals("Pumpwartinide", BountyTracker.chem());
	}

	@Test
	void sunkenTreasureAndOtherLinesMoveNothing() {
		BountyTracker.onMessage("MCLabs » Vote registered! Claim your vote rewards with /claimvotes");
		BountyTracker.onMessage("Bounty » Use /bounty track in Spawn to track one.");
		assertFalse(BountyTracker.isActive());
	}
}
