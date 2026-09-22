package dev.jade.labsaddons.raidmine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RaidMineScoreboardTest {
	@Test
	public void matchesARaidSidebarWhateverItsCase() {
		assertTrue(RaidMineScoreboard.matchesRaid("RAID"));
		assertTrue(RaidMineScoreboard.matchesRaid("MCLabs Raid"));
		assertTrue(RaidMineScoreboard.matchesRaid("raid mine"));
	}

	@Test
	public void ignoresOtherSidebars() {
		assertFalse(RaidMineScoreboard.matchesRaid("MCLabs"));
		assertFalse(RaidMineScoreboard.matchesRaid(""));
		assertFalse(RaidMineScoreboard.matchesRaid(null));
	}
}
