package dev.jade.labsaddons.config;

/**
 * One completed runner job in a runner's history (see
 * {@link RunnerStats#recentJobs}). Persisted so the per-runner "recent jobs"
 * screen survives relogs. {@code drug}/{@code qty} are best-effort: the completed
 * chat line carries only the payout, so they are correlated from the matching
 * posted job (see {@code RunnerTracker}) unless the completed line names them.
 */
public class RunnerJob {
	public String drug = "";
	public int qty = 0;
	public double value = 0.0;
	public long completedMs = 0L;
	public long durationMs = 0L;

	public RunnerJob() {
	}

	public RunnerJob(String drug, int qty, double value, long completedMs, long durationMs) {
		this.drug = drug;
		this.qty = qty;
		this.value = value;
		this.completedMs = completedMs;
		this.durationMs = durationMs;
	}

	public void sanitize() {
		if (drug == null) {
			drug = "";
		}
		qty = Math.max(0, qty);
		value = Math.max(0.0, value);
		completedMs = Math.max(0L, completedMs);
		durationMs = Math.max(0L, durationMs);
	}
}
