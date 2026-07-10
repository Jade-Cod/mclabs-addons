package dev.jade.labsaddons.runner;

import dev.jade.labsaddons.config.RunnerJob;
import dev.jade.labsaddons.config.RunnerStats;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RunnerLeaderboardTest {
	private static RunnerStats stats(int completed, int failed, double value, long totalMs) {
		RunnerStats s = new RunnerStats();
		s.completed = completed;
		s.failed = failed;
		s.valueSold = value;
		s.totalCompletionMs = totalMs;
		return s;
	}

	private static List<String> names(List<RunnerLeaderboard.Entry> ranked) {
		return ranked.stream().map(RunnerLeaderboard.Entry::name).toList();
	}

	@Test
	public void ranksByCompletedDescending() {
		Map<String, RunnerStats> map = new LinkedHashMap<>();
		map.put("Alex", stats(5, 1, 100, 0));
		map.put("FantasyTagz", stats(92, 3, 2_100_000, 0));
		map.put("Steve", stats(40, 2, 840_000, 0));

		assertEquals(List.of("FantasyTagz", "Steve", "Alex"),
				names(RunnerLeaderboard.ranked(map)));
	}

	@Test
	public void breaksCompletedTieBySuccessRate() {
		Map<String, RunnerStats> map = new LinkedHashMap<>();
		map.put("LowRate", stats(10, 10, 999, 0));   // 50%
		map.put("HighRate", stats(10, 0, 1, 0));      // 100%

		assertEquals("HighRate", RunnerLeaderboard.ranked(map).get(0).name());
	}

	@Test
	public void breaksSuccessRateTieByValue() {
		Map<String, RunnerStats> map = new LinkedHashMap<>();
		map.put("LowValue", stats(10, 0, 100, 0));
		map.put("HighValue", stats(10, 0, 500, 0));

		assertEquals("HighValue", RunnerLeaderboard.ranked(map).get(0).name());
	}

	@Test
	public void emptyOrNullMapYieldsEmptyList() {
		assertTrue(RunnerLeaderboard.ranked(new LinkedHashMap<>()).isEmpty());
		assertTrue(RunnerLeaderboard.ranked(null).isEmpty());
	}

	@Test
	public void statsDeriveRateAndAverage() {
		RunnerStats s = stats(3, 1, 0, 6_000);
		assertEquals(0.75, s.successRate(), 1e-9);
		assertEquals(2_000L, s.avgTimeMs());

		RunnerStats none = new RunnerStats();
		assertEquals(0.0, none.successRate(), 1e-9);
		assertEquals(0L, none.avgTimeMs());
	}

	@Test
	public void sanitizeTrimsJobHistoryToCap() {
		RunnerStats s = new RunnerStats();
		for (int i = 0; i < RunnerStats.MAX_JOBS + 25; i++) {
			s.recentJobs.add(new RunnerJob("Cactium", 1, 100, i + 1, 1000));
		}
		s.sanitize();
		assertEquals(RunnerStats.MAX_JOBS, s.recentJobs.size());
		// Newest are kept (oldest dropped): last element is the final one added.
		assertEquals(RunnerStats.MAX_JOBS + 25, s.recentJobs.get(s.recentJobs.size() - 1).completedMs);
	}

	@Test
	public void sanitizeClampsNegativeJobFields() {
		RunnerStats s = new RunnerStats();
		s.recentJobs.add(new RunnerJob(null, -3, -50, -1, -10));
		s.sanitize();
		RunnerJob job = s.recentJobs.get(0);
		assertEquals("", job.drug);
		assertEquals(0, job.qty);
		assertEquals(0.0, job.value, 1e-9);
	}
}
