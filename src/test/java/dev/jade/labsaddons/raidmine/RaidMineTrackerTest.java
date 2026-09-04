package dev.jade.labsaddons.raidmine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RaidMineTrackerTest {
	private static final long NOW = 1_000_000L;

	@BeforeEach
	public void reset() {
		RaidMineTracker.clear();
	}

	@Test
	public void fifteenSecondProcStartsTimer() {
		RaidMineTracker.onMessage("MCLabs » Double mine drops for 15 seconds!", NOW);
		assertEquals(15_000L, RaidMineTracker.remainingMs(NOW));
	}

	@Test
	public void thirtySecondProcStartsTimer() {
		RaidMineTracker.onMessage("MCLabs » Double mine drops for 30 seconds!", NOW);
		assertEquals(30_000L, RaidMineTracker.remainingMs(NOW));
	}

	@Test
	public void procStacksOnRemainingTime() {
		RaidMineTracker.onMessage("MCLabs » Double mine drops for 15 seconds!", NOW);
		// 8s in, 7s left: a second 15s proc should leave 22s, not 15s.
		RaidMineTracker.onMessage("MCLabs » Double mine drops for 15 seconds!", NOW + 8_000L);
		assertEquals(22_000L, RaidMineTracker.remainingMs(NOW + 8_000L));
	}

	@Test
	public void procAfterExpiryDoesNotStackOnStaleTime() {
		RaidMineTracker.onMessage("MCLabs » Double mine drops for 15 seconds!", NOW);
		long later = NOW + 60_000L;
		RaidMineTracker.onMessage("MCLabs » Double mine drops for 30 seconds!", later);
		assertEquals(30_000L, RaidMineTracker.remainingMs(later));
	}

	@Test
	public void timerRunsOut() {
		RaidMineTracker.onMessage("MCLabs » Double mine drops for 15 seconds!", NOW);
		assertEquals(0L, RaidMineTracker.remainingMs(NOW + 15_001L));
	}

	@Test
	public void unrelatedChatIsIgnored() {
		RaidMineTracker.onMessage("MCLabs » The pit is currently open for another 30m:00s!", NOW);
		assertEquals(0L, RaidMineTracker.remainingMs(NOW));
	}
}
