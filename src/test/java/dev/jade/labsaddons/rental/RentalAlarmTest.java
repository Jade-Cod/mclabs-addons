package dev.jade.labsaddons.rental;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The threshold and rearm decision, which {@code RentalAlarm.check} turns into a title and beeps. Each test uses its own rental name because the alerted set is static. */
class RentalAlarmTest {
	private static final long NOW = 1_000_000_000L;
	private static final long MIN = 60_000L;
	private static final long THRESHOLD = 10 * MIN;

	private static RentalEntry rental(String name, long endMs) {
		return new RentalEntry("minecraft:acacia_boat", name, "Zemperus", endMs);
	}

	@Test
	void ringsOnceAtTheThresholdAndNotAgainWhileStillInside() {
		RentalEntry raft = rental("RaftOnce", NOW + THRESHOLD);
		assertEquals(List.of(raft), RentalAlarm.due(List.of(raft), NOW, THRESHOLD, true));
		assertTrue(RentalAlarm.due(List.of(raft), NOW + MIN, THRESHOLD, true).isEmpty());
	}

	@Test
	void staysQuietOneMillisecondOutsideTheThreshold() {
		RentalEntry raft = rental("RaftOutside", NOW + THRESHOLD + 1);
		assertTrue(RentalAlarm.due(List.of(raft), NOW, THRESHOLD, true).isEmpty());
	}

	@Test
	void anExtensionPastTheLineRearmsIt() {
		RentalEntry soon = rental("RaftRearm", NOW + 5 * MIN);
		RentalEntry extended = rental("RaftRearm", NOW + 180 * MIN);
		assertEquals(1, RentalAlarm.due(List.of(soon), NOW, THRESHOLD, true).size());
		assertTrue(RentalAlarm.due(List.of(extended), NOW, THRESHOLD, true).isEmpty());
		assertEquals(1, RentalAlarm.due(List.of(extended), NOW + 175 * MIN, THRESHOLD, true).size());
	}

	@Test
	void anOverdueRentalNeverRings() {
		assertTrue(RentalAlarm.due(List.of(rental("RaftOverdue", NOW - MIN)), NOW, THRESHOLD, true).isEmpty());
		assertTrue(RentalAlarm.due(List.of(rental("RaftExactEnd", NOW)), NOW, THRESHOLD, true).isEmpty());
	}

	@Test
	void disabledNeverRingsAndDoesNotUseUpTheOneShot() {
		RentalEntry raft = rental("RaftDisabled", NOW + MIN);
		assertTrue(RentalAlarm.due(List.of(raft), NOW, THRESHOLD, false).isEmpty());
		assertEquals(List.of(raft), RentalAlarm.due(List.of(raft), NOW, THRESHOLD, true));
	}

	@Test
	void eachRentalRingsOnItsOwn() {
		RentalEntry a = rental("RaftA", NOW + MIN);
		RentalEntry b = rental("RaftB", NOW + 2 * MIN);
		RentalEntry far = rental("RaftFar", NOW + 60 * MIN);
		assertEquals(List.of(a, b), RentalAlarm.due(List.of(a, b, far), NOW, THRESHOLD, true));
	}
}
