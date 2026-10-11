package dev.jade.labsaddons.daily;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The 9 PM Pacific reset, driven through {@code boundaryMs(now)} so the DST days are reachable. */
class DailyResetTest {
	private static final ZoneId LA = ZoneId.of("America/Los_Angeles");

	private static ZonedDateTime la(int year, int month, int day, int hour, int minute) {
		return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, LA);
	}

	private static long utc(String instant) {
		return Instant.parse(instant).toEpochMilli();
	}

	@Test
	void beforeNinePmTheBoundaryIsYesterdaysNinePm() {
		// 2026-10-09 21:00 PDT is 04:00Z on the 10th.
		assertEquals(utc("2026-10-10T04:00:00Z"), DailyReset.boundaryMs(la(2026, 10, 10, 20, 59)));
	}

	@Test
	void atNinePmSharpTheNewBoundaryHasPassed() {
		assertEquals(utc("2026-10-11T04:00:00Z"), DailyReset.boundaryMs(la(2026, 10, 10, 21, 0)));
		assertEquals(utc("2026-10-11T04:00:00Z"), DailyReset.boundaryMs(la(2026, 10, 10, 23, 59)));
	}

	@Test
	void justAfterMidnightStillBelongsToTheNinePmBefore() {
		assertEquals(utc("2026-10-10T04:00:00Z"), DailyReset.boundaryMs(la(2026, 10, 10, 0, 0)));
	}

	@Test
	void fallBackDayOnTheEveningSideOfTheBoundary() {
		// 2026-11-01 DST ends. 20:59 PST is still the Oct 31 21:00 PDT boundary (04:00Z)...
		assertEquals(utc("2026-11-01T04:00:00Z"), DailyReset.boundaryMs(la(2026, 11, 1, 20, 59)));
		// ...and 21:00 PST flips to the new one, which is 05:00Z because the offset is now -8.
		assertEquals(utc("2026-11-02T05:00:00Z"), DailyReset.boundaryMs(la(2026, 11, 1, 21, 0)));
	}

	@Test
	void fallBackDayMorningDoesNotMoveTheBoundary() {
		long expected = utc("2026-11-01T04:00:00Z");
		assertEquals(expected, DailyReset.boundaryMs(la(2026, 11, 1, 0, 30)));
		assertEquals(expected, DailyReset.boundaryMs(la(2026, 11, 1, 3, 0)));
	}

	@Test
	void fallBackCycleIsTwentyFiveHoursLong() {
		long before = DailyReset.boundaryMs(la(2026, 11, 1, 20, 59));
		long after = DailyReset.boundaryMs(la(2026, 11, 1, 21, 0));
		assertEquals(25 * 3_600_000L, after - before);
	}

	@Test
	void springForwardDayOnTheEveningSideOfTheBoundary() {
		// 2027-03-14 DST starts. 20:59 PDT is still the Mar 13 21:00 PST boundary (05:00Z)...
		assertEquals(utc("2027-03-14T05:00:00Z"), DailyReset.boundaryMs(la(2027, 3, 14, 20, 59)));
		// ...and 21:00 PDT flips to the new one at 04:00Z.
		assertEquals(utc("2027-03-15T04:00:00Z"), DailyReset.boundaryMs(la(2027, 3, 14, 21, 0)));
	}

	@Test
	void springForwardDayMorningDoesNotMoveTheBoundary() {
		long expected = utc("2027-03-14T05:00:00Z");
		assertEquals(expected, DailyReset.boundaryMs(la(2027, 3, 14, 1, 59)));
		assertEquals(expected, DailyReset.boundaryMs(la(2027, 3, 14, 3, 30)));
	}

	@Test
	void springForwardCycleIsTwentyThreeHoursLong() {
		long before = DailyReset.boundaryMs(la(2027, 3, 14, 20, 59));
		long after = DailyReset.boundaryMs(la(2027, 3, 14, 21, 0));
		assertEquals(23 * 3_600_000L, after - before);
	}

	@Test
	void aClaimBeforeTheBoundaryIsPendingAndOneAtOrAfterIsNot() {
		long boundary = DailyReset.currentBoundaryMs();
		assertTrue(DailyReset.isPending(boundary - 1));
		assertFalse(DailyReset.isPending(boundary));
		assertFalse(DailyReset.isPending(boundary + 1));
		assertTrue(DailyReset.isPending(0L));
	}

	@Test
	void theCurrentBoundaryIsInThePastAndWithinTwentyFiveHours() {
		long boundary = DailyReset.currentBoundaryMs();
		long age = System.currentTimeMillis() - boundary;
		assertTrue(age >= 0 && age <= 25 * 3_600_000L);
	}
}
