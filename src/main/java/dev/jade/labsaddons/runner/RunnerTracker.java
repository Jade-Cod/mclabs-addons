package dev.jade.labsaddons.runner;

import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.config.RunnerJob;
import dev.jade.labsaddons.config.RunnerStats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks runner job events from chat. Two layers:
 * <ul>
 *   <li><b>Session counters</b> (posted/completed/failed/earned) drive the Runner
 *       Jobs HUD widget and reset each session.</li>
 *   <li><b>Per-runner all-time stats + job history</b> ({@link RunnerStats},
 *       persisted in the config) drive the leaderboard and per-runner screens.</li>
 * </ul>
 * Parsing lives in {@link RunnerMessages}. A job's duration is measured from when
 * a runner <b>takes</b> it to when they <b>complete/fail</b> it, correlated by
 * runner name — which also carries the drug/qty onto the (drug-less) completion.
 * "Posted" is the count of your outstanding jobs: it increments on the post
 * message, decrements as jobs complete or fail, and is corrected to the exact
 * value by the {@code /supplier} scrape ({@link #reconcilePosted}). The
 * taken→complete map is session-only.
 */
public final class RunnerTracker {
	private static int postedJobs = 0;
	private static int completedJobs = 0;
	private static int failedJobs = 0;
	private static double totalEarned = 0.0;

	/** A job a runner has taken but not yet finished (for duration + drug/qty). */
	private record PendingJob(String drug, int qty, long takenMs) {
	}

	// ponytail: one active job per runner (they take → finish → take again). A second
	// "taken" before finishing overwrites the first — good enough; session-only.
	private static final Map<String, PendingJob> pendingByRunner = new HashMap<>();

	private RunnerTracker() {
	}

	public static synchronized void onMessage(String text) {
		RunnerMessages.Event event = RunnerMessages.parse(text);
		if (event == null) {
			return;
		}
		switch (event.type()) {
			case POSTED -> postedJobs++;
			case TAKEN -> pendingByRunner.put(event.runner(),
					new PendingJob(event.drug(), event.qty(), System.currentTimeMillis()));
			case COMPLETED -> {
				completedJobs++;
				postedJobs = Math.max(0, postedJobs - 1);
				totalEarned += event.value();
				recordCompleted(event, pendingByRunner.remove(event.runner()));
				RunnerAlarm.checkThreshold();
			}
			case FAILED -> {
				failedJobs++;
				postedJobs = Math.max(0, postedJobs - 1);
				pendingByRunner.remove(event.runner()); // failed jobs don't count toward avg time
				recordFailed(event.runner());
				RunnerAlarm.checkThreshold();
			}
		}
	}

	/** Set the outstanding posted count from a /supplier scrape (see {@link SupplierJobsReader}). */
	public static synchronized void reconcilePosted(int openCount) {
		postedJobs = Math.max(0, openCount);
		RunnerAlarm.checkThreshold();
	}

	private static void recordCompleted(RunnerMessages.Event event, PendingJob pending) {
		String runner = event.runner();
		if (runner == null || runner.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		long duration = pending == null ? 0L : Math.max(0L, now - pending.takenMs());
		String drug = pending == null ? "" : pending.drug();
		int qty = pending == null ? 0 : pending.qty();

		LabsAddonsConfig config = LabsAddonsConfig.get();
		RunnerStats stats = config.runnerStats.computeIfAbsent(runner, k -> new RunnerStats());
		stats.completed++;
		stats.valueSold += event.value();
		stats.totalCompletionMs += duration;
		stats.recentJobs.add(new RunnerJob(drug, qty, event.value(), now, duration));
		while (stats.recentJobs.size() > RunnerStats.MAX_JOBS) {
			stats.recentJobs.remove(0);
		}
		config.saveAsync();
	}

	private static void recordFailed(String runner) {
		if (runner == null || runner.isEmpty()) {
			return;
		}
		LabsAddonsConfig config = LabsAddonsConfig.get();
		RunnerStats stats = config.runnerStats.computeIfAbsent(runner, k -> new RunnerStats());
		stats.failed++;
		config.saveAsync();
	}

	/** A snapshot copy of the leaderboard map for safe rendering (avoids CME on the live map). */
	public static synchronized Map<String, RunnerStats> statsSnapshot() {
		return new LinkedHashMap<>(LabsAddonsConfig.get().runnerStats);
	}

	/** A runner's completed jobs, newest first, as a defensive copy (for the detail screen). */
	public static synchronized List<RunnerJob> recentJobs(String runner) {
		RunnerStats stats = LabsAddonsConfig.get().runnerStats.get(runner);
		if (stats == null || stats.recentJobs == null) {
			return List.of();
		}
		List<RunnerJob> copy = new ArrayList<>(stats.recentJobs);
		Collections.reverse(copy);
		return copy;
	}

	/** Clear all per-runner leaderboard stats and history (the screen's "Reset Leaderboard"). */
	public static synchronized void resetLeaderboard() {
		LabsAddonsConfig config = LabsAddonsConfig.get();
		config.runnerStats.clear();
		config.saveAsync();
	}

	/** True once any runner event has been seen this session (HUD widget visibility). */
	public static synchronized boolean hasActivity() {
		return postedJobs > 0 || completedJobs > 0 || failedJobs > 0;
	}

	public static synchronized int postedJobs() {
		return postedJobs;
	}

	public static synchronized int completedJobs() {
		return completedJobs;
	}

	public static synchronized int failedJobs() {
		return failedJobs;
	}

	public static synchronized double totalEarned() {
		return totalEarned;
	}

	/** Zero the session counters (the HUD widget's "Reset Session"); leaderboard untouched. */
	public static synchronized void resetSession() {
		postedJobs = 0;
		completedJobs = 0;
		failedJobs = 0;
		totalEarned = 0.0;
		pendingByRunner.clear();
		RunnerAlarm.reset();
	}
}
