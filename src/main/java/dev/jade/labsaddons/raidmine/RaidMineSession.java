package dev.jade.labsaddons.raidmine;

import dev.jade.labsaddons.config.LabsAddonsConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Session totals for resources gathered in the Raid Mine, plus the per-hour rate
 * they are coming in at. Session-scoped and in-memory like the Runner Jobs
 * counters, and cleared only by the widget's Reset Session action.
 *
 * <p>The rate is measured from the <b>first</b> gain rather than from when the
 * widget appeared, so standing in the mine before starting doesn't drag it down.
 */
public final class RaidMineSession {
	private static final long MS_PER_HOUR = 3_600_000L;
	/** Below this, a rate is extrapolated from too little time to mean anything. */
	private static final long MIN_ELAPSED_MS = 5_000L;

	/** A resource's running total, in the colour the server draws it. */
	public record Row(String code, double total, int color, double perHour) {
	}

	// Insertion-ordered so first-seen resources keep a stable place in the widget.
	private static final Map<String, Double> totals = new LinkedHashMap<>();
	private static final Map<String, Integer> colors = new LinkedHashMap<>();
	private static long firstGainMs;
	private static long lastGainMs;

	private RaidMineSession() {
	}

	public static void record(List<RaidMineGains.Gain> gains) {
		record(gains, System.currentTimeMillis());
	}

	static void record(List<RaidMineGains.Gain> gains, long nowMs) {
		if (gains.isEmpty()) {
			return;
		}
		if (firstGainMs == 0L) {
			firstGainMs = nowMs;
		}
		lastGainMs = nowMs;
		for (RaidMineGains.Gain gain : gains) {
			totals.merge(gain.code(), gain.amount(), Double::sum);
			colors.put(gain.code(), gain.color());
		}
	}

	/**
	 * Rows for the widget, biggest total first. Every resource is returned; hiding
	 * is the widget's business, and keeping config out of here is what lets the
	 * totals and rates be unit-tested at all.
	 */
	public static List<Row> rows() {
		return rows(System.currentTimeMillis());
	}

	static List<Row> rows(long nowMs) {
		long elapsed = elapsedMs(nowMs);
		List<Row> rows = new ArrayList<>();
		for (Map.Entry<String, Double> entry : totals.entrySet()) {
			double perHour = elapsed < MIN_ELAPSED_MS
					? 0.0
					: entry.getValue() * MS_PER_HOUR / elapsed;
			rows.add(new Row(entry.getKey(), entry.getValue(),
					colors.getOrDefault(entry.getKey(), 0xFFFFFF), perHour));
		}
		rows.sort(Comparator.comparingDouble(Row::total).reversed());
		return rows;
	}

	/** Every resource seen this session, hidden ones included (for the editor's toggles). */
	public static List<String> knownCodes() {
		return new ArrayList<>(totals.keySet());
	}

	/**
	 * Rate window: from the first gain to the last, not to now — so a session left
	 * running while you stand idle stops diluting the figure you were mining at.
	 */
	private static long elapsedMs(long nowMs) {
		if (firstGainMs == 0L) {
			return 0L;
		}
		return Math.max(0L, Math.max(lastGainMs, firstGainMs) - firstGainMs);
	}

	public static boolean hasActivity() {
		return !totals.isEmpty();
	}

	public static boolean isHidden(String code) {
		return LabsAddonsConfig.get().hiddenRaidMineCodes.contains(code);
	}

	public static void setHidden(String code, boolean hidden) {
		LabsAddonsConfig config = LabsAddonsConfig.get();
		if (hidden) {
			config.hiddenRaidMineCodes.add(code);
		} else {
			config.hiddenRaidMineCodes.remove(code);
		}
		config.saveAsync();
	}

	/** The widget's Reset Session action; the hidden-resource choices are kept. */
	public static void resetSession() {
		totals.clear();
		colors.clear();
		firstGainMs = 0L;
		lastGainMs = 0L;
	}
}
