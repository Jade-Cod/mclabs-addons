package dev.jade.labsaddons.config;

import dev.jade.labsaddons.chum.ChumTimer;
import dev.jade.labsaddons.event.MiniEventTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lines are from scenarios/minievent-mining.jsonl; the fishing line is the same shape.
 * Lives in the config package for {@code useStore}.
 */
class MiniEventTrackerTest {
	private static final long SLACK_MS = 5_000L;
	private static final long MIN = 60_000L;
	private static final long DEBOUNCE_MS = 30_000L;

	private static final String UPCOMING = "Mini-Event » A Mini-Event will begin in 10 minutes! /minievent";
	private static final String BEGUN = "Mini-Event » Mining event has begun! Mine ores in the Raid Mines (/raid) to earn points. \n"
			+ "Earn at least 750 points for a bonus 0.25EP.\nMini-event ends in 20 minutes.\n\n"
			+ "First place rewards: $5 Store Credit, $500k, 1 Event Point\n\n/minievent";
	private static final String FISHING = "Mini-Event » Fishing event has begun! Catch fish to earn points.\n"
			+ "Mini-event ends in 20 minutes.\n\n/minievent";
	private static final String STATUS = "Mini-Event » Mining mini-event: Mine ores in the Raid Mines (/raid)\n"
			+ "Current top players:\n#1. Ophiliah\nReach 750+ score for 0.25 Event Points!\n"
			+ "Mini-event ends in 12:14\nMore info: /minievent";

	@TempDir
	Path configDir;

	@BeforeEach
	void freshConfig() throws ReflectiveOperationException {
		LabsAddonsConfig.useStore(new ConfigStore(configDir));
		MiniEventTracker.clear();
		ChumTimer.reset();
		setLastFishingGrant(0L);
	}

	private static void setLastFishingGrant(long value) throws ReflectiveOperationException {
		Field f = MiniEventTracker.class.getDeclaredField("lastFishingGrantMs");
		f.setAccessible(true);
		f.setLong(null, value);
	}

	@Test
	void upcomingLineStartsTheCountdown() {
		MiniEventTracker.onMessage(UPCOMING);
		assertTrue(MiniEventTracker.isUpcoming());
		assertFalse(MiniEventTracker.isActive());
		assertEquals(10 * MIN, MiniEventTracker.upcomingRemainingMs(), SLACK_MS);
	}

	@Test
	void upcomingLineDoesNotCountAsTheEventStarting() {
		MiniEventTracker.onMessage(UPCOMING);
		assertEquals("", MiniEventTracker.type());
	}

	@Test
	void begunLineSetsTypeAndTheEndTimeFromItsOwnEndsLine() {
		MiniEventTracker.onMessage(BEGUN);
		assertTrue(MiniEventTracker.isActive());
		assertEquals("Mining", MiniEventTracker.type());
		assertEquals(20 * MIN, MiniEventTracker.activeRemainingMs(), SLACK_MS);
	}

	@Test
	void begunLineWithoutAnEndsLineFallsBackToThirtyMinutes() {
		MiniEventTracker.onMessage("Mini-Event » Mining event has begun! Mine ores in the Raid Mines (/raid) to earn points.");
		assertEquals(30 * MIN, MiniEventTracker.activeRemainingMs(), SLACK_MS);
	}

	@Test
	void begunClearsTheUpcomingCountdown() {
		MiniEventTracker.onMessage(UPCOMING);
		MiniEventTracker.onMessage(BEGUN);
		assertEquals(0L, MiniEventTracker.upcomingRemainingMs());
		assertFalse(MiniEventTracker.isUpcoming());
	}

	@Test
	void statusReplyWithClockEndTimeResyncs() {
		MiniEventTracker.onMessage(BEGUN);
		MiniEventTracker.onMessage(STATUS);
		assertEquals(12 * MIN + 14_000L, MiniEventTracker.activeRemainingMs(), SLACK_MS);
	}

	@Test
	void endsLineWithHoursClock() {
		MiniEventTracker.onMessage(BEGUN);
		MiniEventTracker.onMessage("Mini-event ends in 1:02:03\n");
		assertEquals(62 * MIN + 3_000L, MiniEventTracker.activeRemainingMs(), SLACK_MS);
	}

	@Test
	void endsLineIsIgnoredWhenNoEventIsActive() {
		MiniEventTracker.onMessage(STATUS);
		assertFalse(MiniEventTracker.isActive());
		assertEquals(0L, MiniEventTracker.activeRemainingMs());
	}

	@Test
	void unitFormatsInUpcomingLine() {
		MiniEventTracker.onMessage("Mini-Event » A Mini-Event will begin in 1 hour 5 minutes!");
		assertEquals(65 * MIN, MiniEventTracker.upcomingRemainingMs(), SLACK_MS);
		MiniEventTracker.onMessage("Mini-Event » A Mini-Event will begin in 30 seconds!");
		assertEquals(30_000L, MiniEventTracker.upcomingRemainingMs(), SLACK_MS);
	}

	@Test
	void expiredEventStopsBeingActive() {
		MiniEventTracker.onMessage(BEGUN);
		LabsAddonsConfig.get().miniEventExpiryEpochMs = System.currentTimeMillis() - 1_000L;
		assertFalse(MiniEventTracker.isActive());
		assertEquals(0L, MiniEventTracker.activeRemainingMs());
	}

	@Test
	void clearDropsEverything() {
		MiniEventTracker.onMessage(UPCOMING);
		MiniEventTracker.onMessage(BEGUN);
		MiniEventTracker.clear();
		assertEquals("", MiniEventTracker.type());
		assertFalse(MiniEventTracker.isActive());
		assertFalse(MiniEventTracker.isUpcoming());
	}

	@Test
	void fishingEventGrantsThirtyMinutesOfChum() {
		MiniEventTracker.onMessage(FISHING);
		assertEquals("Fishing", MiniEventTracker.type());
		assertEquals(30 * MIN, ChumTimer.remainingMs(), SLACK_MS);
	}

	@Test
	void fishingGrantStacksOnExistingChum() {
		ChumTimer.addChum();
		MiniEventTracker.onMessage(FISHING);
		assertEquals(50 * MIN, ChumTimer.remainingMs(), SLACK_MS);
	}

	@Test
	void secondFishingBeginWithinThirtySecondsGrantsNothing() {
		MiniEventTracker.onMessage(FISHING);
		MiniEventTracker.onMessage(FISHING);
		assertEquals(30 * MIN, ChumTimer.remainingMs(), SLACK_MS);
	}

	@Test
	void fishingBeginAfterTheDebounceGrantsAgain() throws ReflectiveOperationException {
		MiniEventTracker.onMessage(FISHING);
		setLastFishingGrant(System.currentTimeMillis() - DEBOUNCE_MS - 1_000L);
		MiniEventTracker.onMessage(FISHING);
		assertEquals(60 * MIN, ChumTimer.remainingMs(), SLACK_MS);
	}

	@Test
	void nonFishingEventGrantsNoChum() {
		MiniEventTracker.onMessage(BEGUN);
		assertFalse(ChumTimer.isActive());
	}

	@Test
	void unrelatedLinesMoveNothing() {
		MiniEventTracker.onMessage("Booster » Booster activated! All Chems boosted 2x by Zemperus for 30m.");
		assertEquals("", MiniEventTracker.type());
		assertFalse(MiniEventTracker.isUpcoming());
	}
}
