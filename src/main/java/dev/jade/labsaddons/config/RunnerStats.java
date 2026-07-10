package dev.jade.labsaddons.config;

/**
 * All-time stats for one runner who completes your posted jobs, keyed by
 * username in {@link LabsAddonsConfig#runnerStats}. Persisted so the leaderboard
 * survives relogs. {@code totalCompletionMs} is the summed (best-effort)
 * posted&rarr;completed duration across completed jobs, from which
 * {@link #avgTimeMs()} is derived.
 */
public class RunnerStats {
	/** Cap on stored completed-job history per runner (oldest dropped). */
	public static final int MAX_JOBS = 50;
	/** Cap on how many runners persist. A foreign/troll server can post unique
	 *  completer names, so bound the map (eldest dropped) to keep the config file
	 *  from growing without limit; real MCLabs use never approaches this. */
	public static final int MAX_RUNNERS = 500;

	public int completed = 0;
	public int failed = 0;
	public double valueSold = 0.0;
	public long totalCompletionMs = 0L;
	/** Recent completed jobs, oldest first (newest appended). Bounded to {@link #MAX_JOBS}. */
	public java.util.List<RunnerJob> recentJobs = new java.util.ArrayList<>();

	public RunnerStats() {
	}

	/** Completion rate 0..1 across all attributed jobs (0 when none seen). */
	public double successRate() {
		int total = completed + failed;
		return total <= 0 ? 0.0 : (double) completed / total;
	}

	/** Mean posted&rarr;completed duration in ms (0 when nothing completed). */
	public long avgTimeMs() {
		return completed <= 0 ? 0L : totalCompletionMs / completed;
	}

	/** Clamp any negative persisted values to zero. */
	public void sanitize() {
		completed = Math.max(0, completed);
		failed = Math.max(0, failed);
		valueSold = Math.max(0.0, valueSold);
		totalCompletionMs = Math.max(0L, totalCompletionMs);
		if (recentJobs == null) {
			recentJobs = new java.util.ArrayList<>();
		}
		recentJobs.removeIf(java.util.Objects::isNull);
		for (RunnerJob job : recentJobs) {
			job.sanitize();
		}
		// Keep only the most recent MAX_JOBS (newest are at the end).
		if (recentJobs.size() > MAX_JOBS) {
			recentJobs = new java.util.ArrayList<>(
					recentJobs.subList(recentJobs.size() - MAX_JOBS, recentJobs.size()));
		}
	}
}
