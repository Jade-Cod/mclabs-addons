package dev.jade.labsaddons.runner;

import dev.jade.labsaddons.config.RunnerStats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Pure ranking of the runner leaderboard — no Minecraft or FabricLoader deps, so
 * it unit-tests directly. Runners are ranked by <b>jobs completed</b> (desc),
 * tie-broken by success rate then value sold, and finally by name for a stable
 * order.
 */
public final class RunnerLeaderboard {
	private RunnerLeaderboard() {
	}

	/** One ranked row: the runner's name paired with its stats. */
	public record Entry(String name, RunnerStats stats) {
	}

	private static final Comparator<Entry> ORDER = Comparator
			.comparingInt((Entry e) -> e.stats().completed)
			.thenComparingDouble(e -> e.stats().successRate())
			.thenComparingDouble(e -> e.stats().valueSold)
			.reversed()
			.thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER);

	/** Runners sorted best-first. Null map/keys/values are skipped defensively. */
	public static List<Entry> ranked(Map<String, RunnerStats> stats) {
		List<Entry> entries = new ArrayList<>();
		if (stats != null) {
			stats.forEach((name, s) -> {
				if (name != null && s != null) {
					entries.add(new Entry(name, s));
				}
			});
		}
		entries.sort(ORDER);
		return entries;
	}
}
