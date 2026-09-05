package dev.jade.labsaddons.bounty;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real Fishing Weekend chat lines, prefix and all. */
public class SunkenTreasureTrackerTest {
	private static final String ANNOUNCE = "Fishing Weekend » Sunken Treasure! Find one of the 6 sunken "
			+ "barrels along the shorelines of Spawn for 50 score, a temporary 10% score boost, and a Fish Finder!";
	private static final String FOUND = "Fishing Weekend » _MikeHunt has found a sunken treasure! 4 left "
			+ "in the Spawn waters. Look for bubbles and listen for creaking sounds!";

	@BeforeEach
	public void reset() {
		SunkenTreasureTracker.clear();
	}

	@Test
	public void theAnnouncementSeedsTheCount() {
		SunkenTreasureTracker.onMessage(ANNOUNCE);
		assertTrue(SunkenTreasureTracker.isActive());
		assertEquals(6, SunkenTreasureTracker.remaining());
	}

	@Test
	public void aFindOverwritesTheCount() {
		SunkenTreasureTracker.onMessage(ANNOUNCE);
		SunkenTreasureTracker.onMessage(FOUND);
		assertEquals(4, SunkenTreasureTracker.remaining());
	}

	/** A find with no announcement seen (joined mid-wave) still seeds us. */
	@Test
	public void aFindAloneIsEnough() {
		SunkenTreasureTracker.onMessage(FOUND);
		assertTrue(SunkenTreasureTracker.isActive());
		assertEquals(4, SunkenTreasureTracker.remaining());
	}

	@Test
	public void theNextWaveReseedsTheCount() {
		SunkenTreasureTracker.onMessage(FOUND);
		SunkenTreasureTracker.onMessage(ANNOUNCE);
		assertEquals(6, SunkenTreasureTracker.remaining());
	}

	/** No end-of-event line exists, so the last barrel is what takes the row away. */
	@Test
	public void theLastBarrelEndsIt() {
		SunkenTreasureTracker.onMessage(ANNOUNCE);
		SunkenTreasureTracker.onMessage("Fishing Weekend » Ophiliah has found a sunken treasure! 0 left "
				+ "in the Spawn waters.");
		assertFalse(SunkenTreasureTracker.isActive());
	}

	@Test
	public void oneLeftSpelledOutStillParses() {
		SunkenTreasureTracker.onMessage("Fishing Weekend » Ophiliah has found a sunken treasure! one left "
				+ "in the Spawn waters.");
		assertEquals(1, SunkenTreasureTracker.remaining());
	}

	/** Your own find carries no count — the broadcast line right behind it does. */
	@Test
	public void yourOwnFindChangesNothing() {
		SunkenTreasureTracker.onMessage(ANNOUNCE);
		SunkenTreasureTracker.onMessage("Fishing Weekend » Sunken treasure found for 50 score!");
		assertEquals(6, SunkenTreasureTracker.remaining());
	}

	@Test
	public void unrelatedChatIsIgnored() {
		SunkenTreasureTracker.onMessage("Bounty » There are 2 bounty chests left in Spawn!");
		assertFalse(SunkenTreasureTracker.isActive());
	}

	/** The /fw menu says "crates" where chat says "barrels"; either way it's the count. */
	@Test
	public void theFwMenuLineIsRead() {
		assertEquals(6, SunkenTreasureReader.cratesLeft("6 crates left!"));
		assertEquals(1, SunkenTreasureReader.cratesLeft("1 crate left!"));
		assertNull(SunkenTreasureReader.cratesLeft("Listen for creaking sounds"));
		assertNull(SunkenTreasureReader.cratesLeft("Find sunken crates in the"));
	}

	@Test
	public void theFwMenuOverridesWhatChatToldUs() {
		SunkenTreasureTracker.onMessage(FOUND);
		SunkenTreasureTracker.reconcile(6);
		assertEquals(6, SunkenTreasureTracker.remaining());
		SunkenTreasureTracker.reconcile(0);
		assertFalse(SunkenTreasureTracker.isActive());
	}

	@Test
	public void clearResetsEverything() {
		SunkenTreasureTracker.onMessage(ANNOUNCE);
		SunkenTreasureTracker.clear();
		assertFalse(SunkenTreasureTracker.isActive());
		assertEquals(0, SunkenTreasureTracker.remaining());
	}
}
