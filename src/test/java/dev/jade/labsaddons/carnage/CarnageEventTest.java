package dev.jade.labsaddons.carnage;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarnageEventTest {
	@Test
	void runsFromOctoberFirstToMidnightEasternNovemberFirst() {
		// Both ends fall in daylight time, UTC-4.
		assertEquals(Instant.parse("2026-10-01T04:00:00Z").toEpochMilli(), CarnageEvent.START_MS);
		assertEquals(Instant.parse("2026-11-01T04:00:00Z").toEpochMilli(), CarnageEvent.END_MS);
		assertFalse(CarnageEvent.within(CarnageEvent.START_MS - 1));
		assertTrue(CarnageEvent.within(CarnageEvent.START_MS));
		assertTrue(CarnageEvent.within(CarnageEvent.END_MS - 1));
		assertFalse(CarnageEvent.within(CarnageEvent.END_MS));
	}
}
