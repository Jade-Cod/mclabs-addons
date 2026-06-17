package dev.jade.fishbite.daily;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Daily reset boundary for MCLabs dailies/votes: 9 PM Pacific. Computed as an
 * absolute instant in {@code America/Los_Angeles} (DST-correct), so the reset
 * fires at the same real moment for every player regardless of their own
 * timezone — i.e. "9 PM PST adapted to the user's timezone".
 */
public final class DailyReset {
	private static final ZoneId RESET_ZONE = ZoneId.of("America/Los_Angeles");
	private static final int RESET_HOUR = 21;

	private DailyReset() {
	}

	/** Epoch ms of the most recent 9 PM Pacific reset that has already passed. */
	public static long currentBoundaryMs() {
		ZonedDateTime now = ZonedDateTime.now(RESET_ZONE);
		ZonedDateTime todayReset = now.toLocalDate().atTime(RESET_HOUR, 0).atZone(RESET_ZONE);
		ZonedDateTime boundary = now.isBefore(todayReset) ? todayReset.minusDays(1) : todayReset;
		return boundary.toInstant().toEpochMilli();
	}

	/** True if a task last completed at {@code lastDoneMs} is due again this cycle. */
	public static boolean isPending(long lastDoneMs) {
		return lastDoneMs < currentBoundaryMs();
	}
}
