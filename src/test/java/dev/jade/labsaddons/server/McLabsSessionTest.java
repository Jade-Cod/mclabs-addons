package dev.jade.labsaddons.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every sidebar title the server is known to show. Three were captured from real logs and
 * the rest reported from play, and both of the mod's answers are derived from this one
 * string — so a title MCLabs changes fails here rather than silently taking the whole HUD
 * off screen.
 */
class McLabsSessionTest {
	/** The one that means a raid. */
	private static final String RAID = "MCLabs Raid";

	private static final String[] EVERY_TITLE = {
			"MCLabs Spawn",
			"MCLabs Overworld",
			"MCLabs Underworld-A",
			"MCLabs Underworld-B",
			"MCLabs UW-A",
			"MCLabs UW-B",
			"MCLabs Pit",
			"MCLabs Event",
			"MCLabs Lobby",
			RAID
	};

	@Test
	void everyKnownSidebarPutsTheHudOnScreen() {
		for (String title : EVERY_TITLE) {
			assertTrue(McLabsSession.matchesMcLabs(title), title);
		}
	}

	/**
	 * The Pit and the Lobby are the two that had never been checked: the Pit's join banner
	 * omits "MCLabs" and the Lobby sends no banner at all, so if either sidebar dropped the
	 * word the HUD would be invisible in a whole world with nothing to say why.
	 */
	@Test
	void thePitAndTheLobbyAreOnTheNetworkToo() {
		assertTrue(McLabsSession.matchesMcLabs("MCLabs Pit"));
		assertTrue(McLabsSession.matchesMcLabs("MCLabs Lobby"));
	}

	@Test
	void onlyTheRaidSidebarIsARaid() {
		for (String title : EVERY_TITLE) {
			if (title.equals(RAID)) {
				continue;
			}
			assertFalse(McLabsSession.matchesRaid(title), title);
		}
		assertTrue(McLabsSession.matchesRaid(RAID));
	}

	@Test
	void theMatchesAreCaseInsensitive() {
		assertTrue(McLabsSession.matchesMcLabs("mclabs spawn"));
		assertTrue(McLabsSession.matchesRaid("RAID"));
		assertTrue(McLabsSession.matchesRaid("raid mine"));
	}

	/** No world, no sidebar, or another server's board: neither answer is yes. */
	@Test
	void nothingOnTheSidebarIsNotMcLabsAndIsNotARaid() {
		for (String title : new String[] {null, "", "Hypixel", "SkyBlock"}) {
			assertFalse(McLabsSession.matchesMcLabs(title), String.valueOf(title));
			assertFalse(McLabsSession.matchesRaid(title), String.valueOf(title));
		}
	}
}
