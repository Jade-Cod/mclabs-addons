package dev.jade.labsaddons.config;

import dev.jade.labsaddons.runner.RunnerTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** In the config package for {@code useStore}. Lines are scenarios/runner-job-lifecycle.jsonl. */
class RunnerTrackerTest {
	private static final String POSTED = "MCLabs » Runner job posted: 2,176x Carrotenium at 70%.";
	private static final String TAKEN = "Runner » Relston735 has taken your runner job (Carrotenium x2176)";
	private static final String DONE = "Runner » Relston735 has completed your runner job, you've earned $5.53k.";
	private static final String FAILED = "Runner » onveil failed your runner job! (Cactatonate-2-2-2 x2,176)";
	private static final String ONVEIL_TAKEN = "Runner » onveil has taken your runner job (Cactatonate-2-2-2 x2176)";

	@TempDir
	Path configDir;

	@BeforeEach
	void freshConfig() {
		LabsAddonsConfig.useStore(new ConfigStore(configDir));
		RunnerTracker.resetSession();
	}

	private static RunnerStats stats(String runner) {
		return LabsAddonsConfig.get().runnerStats.get(runner);
	}

	private static void pause() throws InterruptedException {
		Thread.sleep(30);
	}

	@Test
	void postingCountsAnOutstandingJob() {
		RunnerTracker.onMessage(POSTED);
		RunnerTracker.onMessage(POSTED);
		assertEquals(2, RunnerTracker.postedJobs());
		assertTrue(RunnerTracker.hasActivity());
		assertEquals(0, RunnerTracker.completedJobs());
	}

	@Test
	void aTakenLineMovesNoCounters() {
		RunnerTracker.onMessage(POSTED);
		RunnerTracker.onMessage(TAKEN);
		assertEquals(1, RunnerTracker.postedJobs());
		assertEquals(0, RunnerTracker.completedJobs());
		assertNull(stats("Relston735"));
	}

	@Test
	void completingTheTakenJobCreditsTheRunnerWithDrugQtyAndDuration() throws InterruptedException {
		RunnerTracker.onMessage(POSTED);
		RunnerTracker.onMessage(TAKEN);
		pause();
		RunnerTracker.onMessage(DONE);

		assertEquals(0, RunnerTracker.postedJobs());
		assertEquals(1, RunnerTracker.completedJobs());
		assertEquals(5_530.0, RunnerTracker.totalEarned(), 1e-6);

		RunnerStats s = stats("Relston735");
		assertEquals(1, s.completed);
		assertEquals(1, s.timedCompleted);
		assertEquals(5_530.0, s.valueSold, 1e-6);
		assertTrue(s.totalCompletionMs >= 25L, "duration runs from taken to completed");
		RunnerJob job = s.recentJobs.get(0);
		assertEquals("Carrotenium", job.drug);
		assertEquals(2176, job.qty);
		assertEquals(5_530.0, job.value, 1e-6);
		assertEquals(s.totalCompletionMs, job.durationMs);
	}

	@Test
	void aFailedJobCountsAgainstTheRunnerAndRecordsNoJob() {
		RunnerTracker.onMessage(POSTED);
		RunnerTracker.onMessage(ONVEIL_TAKEN);
		RunnerTracker.onMessage(FAILED);

		assertEquals(0, RunnerTracker.postedJobs());
		assertEquals(1, RunnerTracker.failedJobs());
		assertEquals(0, RunnerTracker.completedJobs());
		RunnerStats s = stats("onveil");
		assertEquals(1, s.failed);
		assertEquals(0, s.completed);
		assertTrue(s.recentJobs.isEmpty());
		assertEquals(0L, s.avgTimeMs());
	}

	@Test
	void aFailureDropsTheTakenTimerSoALaterBareCompletionIsUntimed() {
		RunnerTracker.onMessage(TAKEN);
		RunnerTracker.onMessage("Runner » Relston735 failed your runner job! (Carrotenium x2176)");
		RunnerTracker.onMessage(DONE);
		assertEquals(0, stats("Relston735").timedCompleted);
		assertEquals(1, stats("Relston735").completed);
	}

	@Test
	void timedAverageIgnoresCompletionsWithNoTakenLine() throws InterruptedException {
		RunnerTracker.onMessage(TAKEN);
		pause();
		RunnerTracker.onMessage(DONE);
		RunnerTracker.onMessage(TAKEN);
		pause();
		RunnerTracker.onMessage(DONE);
		RunnerTracker.onMessage(DONE);

		RunnerStats s = stats("Relston735");
		assertEquals(3, s.completed);
		assertEquals(2, s.timedCompleted);
		assertEquals(s.totalCompletionMs / 2, s.avgTimeMs());
		assertTrue(s.avgTimeMs() >= 25L);
		assertEquals(0L, s.recentJobs.get(2).durationMs);
		assertEquals("", s.recentJobs.get(2).drug);
	}

	@Test
	void successRateCountsCompletedAgainstFailed() {
		RunnerTracker.onMessage("Runner » Relston735 failed your runner job! (Carrotenium x2176)");
		RunnerTracker.onMessage(DONE);
		RunnerTracker.onMessage(DONE);
		RunnerTracker.onMessage(DONE);
		assertEquals(0.75, stats("Relston735").successRate(), 1e-9);
	}

	@Test
	void completionsNeverPushPostedBelowZero() {
		RunnerTracker.onMessage(DONE);
		RunnerTracker.onMessage(FAILED);
		assertEquals(0, RunnerTracker.postedJobs());
		assertEquals(1, RunnerTracker.completedJobs());
		assertEquals(1, RunnerTracker.failedJobs());
	}

	@Test
	void reconcileSetsTheExactOpenCountThenLaterEventsMoveIt() {
		RunnerTracker.onMessage(POSTED);
		RunnerTracker.reconcilePosted(12);
		assertEquals(12, RunnerTracker.postedJobs());
		RunnerTracker.onMessage(DONE);
		assertEquals(11, RunnerTracker.postedJobs());
		RunnerTracker.onMessage(POSTED);
		assertEquals(12, RunnerTracker.postedJobs());
	}

	@Test
	void reconcileClampsNegativeAndAllowsZero() {
		RunnerTracker.reconcilePosted(-4);
		assertEquals(0, RunnerTracker.postedJobs());
		RunnerTracker.reconcilePosted(0);
		assertEquals(0, RunnerTracker.postedJobs());
	}

	@Test
	void reconcileAloneDoesNotCountAsActivity() {
		RunnerTracker.reconcilePosted(0);
		assertFalse(RunnerTracker.hasActivity());
	}

	@Test
	void runnerLinesQuotedInPlayerChatMoveNothing() {
		for (String line : List.of(POSTED, TAKEN, DONE, FAILED)) {
			RunnerTracker.onMessage("[S] [VIP] bob: " + line);
		}
		assertEquals(0, RunnerTracker.postedJobs());
		assertEquals(0, RunnerTracker.completedJobs());
		assertEquals(0, RunnerTracker.failedJobs());
		assertTrue(LabsAddonsConfig.get().runnerStats.isEmpty());
	}

	@Test
	void theBoardSpamAndOtherChatMoveNothing() {
		RunnerTracker.onMessage("Runner » New job posted!");
		RunnerTracker.onMessage("MCLabs » Vote registered! Claim your vote rewards with /claimvotes");
		assertFalse(RunnerTracker.hasActivity());
	}

	@Test
	void resetSessionKeepsTheLeaderboard() {
		RunnerTracker.onMessage(DONE);
		RunnerTracker.resetSession();
		assertEquals(0, RunnerTracker.completedJobs());
		assertEquals(0.0, RunnerTracker.totalEarned());
		assertEquals(1, stats("Relston735").completed);
	}

	@Test
	void resetLeaderboardClearsEveryRunner() {
		RunnerTracker.onMessage(DONE);
		RunnerTracker.resetLeaderboard();
		assertTrue(RunnerTracker.statsSnapshot().isEmpty());
	}

	@Test
	void theSnapshotIsACopy() {
		RunnerTracker.onMessage(DONE);
		Map<String, RunnerStats> snapshot = RunnerTracker.statsSnapshot();
		snapshot.clear();
		assertNotNull(stats("Relston735"));
	}

	@Test
	void jobHistoryKeepsTheNewestFiftyAndListsNewestFirst() {
		for (int i = 1; i <= RunnerStats.MAX_JOBS + 5; i++) {
			RunnerTracker.onMessage("Runner » Relston735 has completed your runner job, you've earned $" + i + ".");
		}
		RunnerStats s = stats("Relston735");
		assertEquals(RunnerStats.MAX_JOBS, s.recentJobs.size());
		assertEquals(6.0, s.recentJobs.get(0).value, 1e-9);
		List<RunnerJob> newestFirst = RunnerTracker.recentJobs("Relston735");
		assertEquals(55.0, newestFirst.get(0).value, 1e-9);
		assertEquals(6.0, newestFirst.get(newestFirst.size() - 1).value, 1e-9);
		assertEquals(55, s.completed);
	}

	@Test
	void recentJobsOfAnUnknownRunnerIsEmpty() {
		assertTrue(RunnerTracker.recentJobs("nobody").isEmpty());
	}

	@Test
	void theLeaderboardStopsAtFiveHundredRunnersAndDropsTheOldest() {
		for (int i = 0; i <= RunnerStats.MAX_RUNNERS; i++) {
			RunnerTracker.onMessage("Runner » user" + String.format("%03d", i)
					+ " failed your runner job! (Carrotenium x1)");
		}
		Map<String, RunnerStats> all = LabsAddonsConfig.get().runnerStats;
		assertEquals(RunnerStats.MAX_RUNNERS, all.size());
		assertFalse(all.containsKey("user000"));
		assertTrue(all.containsKey("user001"));
		assertTrue(all.containsKey("user500"));
	}

	@Test
	void anAlreadyListedRunnerIsNotCountedTwiceTowardTheCap() {
		for (int i = 0; i < RunnerStats.MAX_RUNNERS; i++) {
			RunnerTracker.onMessage("Runner » user" + String.format("%03d", i)
					+ " failed your runner job! (Carrotenium x1)");
		}
		RunnerTracker.onMessage("Runner » user000 failed your runner job! (Carrotenium x1)");
		Map<String, RunnerStats> all = LabsAddonsConfig.get().runnerStats;
		assertEquals(RunnerStats.MAX_RUNNERS, all.size());
		assertEquals(2, all.get("user000").failed);
	}

	// Open question: eviction is by first-seen order, so a runner who is still active but joined
	// early is the one dropped, with their stats. Pinned as it is today.
	@Test
	void capEvictionFollowsFirstSeenOrderNotRecentActivity() {
		for (int i = 0; i < RunnerStats.MAX_RUNNERS; i++) {
			RunnerTracker.onMessage("Runner » user" + String.format("%03d", i)
					+ " failed your runner job! (Carrotenium x1)");
		}
		RunnerTracker.onMessage("Runner » user000 failed your runner job! (Carrotenium x1)");
		RunnerTracker.onMessage("Runner » newcomer failed your runner job! (Carrotenium x1)");
		assertFalse(LabsAddonsConfig.get().runnerStats.containsKey("user000"));
	}

	@Test
	void oldSavedStatsStillAverageOverEveryCompletion() {
		// Saved before timed completions were counted apart: no timedCompleted, so every completion counts.
		RunnerStats old = new RunnerStats();
		old.completed = 4;
		old.totalCompletionMs = 400L;
		assertEquals(100L, old.avgTimeMs());
	}
}
