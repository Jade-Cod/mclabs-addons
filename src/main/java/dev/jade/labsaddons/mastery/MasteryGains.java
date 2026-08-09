package dev.jade.labsaddons.mastery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers which rows gained progress recently, so the HUD can surface just
 * those and then get out of the way.
 *
 * <p>Keyed by row name, and shared by both sources the progress widget draws:
 * Mastery challenges (by quest name) and chem prestige tracks (by chem name).
 * The two name spaces cannot collide — every quest name carries a verb prefix
 * ("Sell to Red Dealer") where a chem is bare ("Cactium").
 *
 * <p>A gain keeps its row alive for {@value #LIFE_MS}ms. Gaining again while the
 * row is still up accumulates into the same {@code +} figure and restarts the
 * clock, so a streak keeps one row alive showing the running total rather than
 * flickering a new row per event. Once the life expires the row fades over
 * {@value #FADE_MS}ms and its accumulated total is dropped.
 *
 * <p>Ordering is most-recent-first, tie-broken by insertion sequence so two gains
 * landing in the same millisecond still have a stable order.
 */
public final class MasteryGains {
	public static final long LIFE_MS = 20_000L;
	public static final long FADE_MS = 400L;

	private record Gain(double delta, long lastMs, long seq) {
	}

	private static final Map<String, Gain> GAINS = new ConcurrentHashMap<>();
	private static long sequence;

	// Injectable so tests can drive the 20s window without sleeping.
	private static java.util.function.LongSupplier clock = System::currentTimeMillis;

	private MasteryGains() {
	}

	static void setClock(java.util.function.LongSupplier source) {
		clock = source == null ? System::currentTimeMillis : source;
	}

	/** Records a gain, accumulating into any still-visible row for the same quest. */
	public static synchronized void record(String questName, double delta) {
		if (questName == null || delta <= 0) {
			return;
		}
		long now = clock.getAsLong();
		Gain existing = GAINS.get(questName);
		double total = existing != null && !isExpired(existing, now) ? existing.delta() + delta : delta;
		GAINS.put(questName, new Gain(total, now, sequence++));
	}

	private static boolean isExpired(Gain gain, long now) {
		return now - gain.lastMs() >= LIFE_MS;
	}

	/** Drops rows whose fade has finished. Called each render. */
	public static void prune() {
		long now = clock.getAsLong();
		GAINS.entrySet().removeIf(e -> now - e.getValue().lastMs() >= LIFE_MS + FADE_MS);
	}

	/** Quest names with a live row, most recently gained first. */
	public static List<String> recentNames() {
		prune();
		List<Map.Entry<String, Gain>> entries = new ArrayList<>(GAINS.entrySet());
		entries.sort(Comparator
				.comparingLong((Map.Entry<String, Gain> e) -> e.getValue().lastMs())
				.thenComparingLong(e -> e.getValue().seq())
				.reversed());
		return entries.stream().map(Map.Entry::getKey).toList();
	}

	/** Accumulated gain shown as the {@code +} figure, or 0 if this quest has no live row. */
	public static double delta(String questName) {
		Gain gain = GAINS.get(questName);
		return gain == null ? 0 : gain.delta();
	}

	/**
	 * Row opacity: fully opaque for its life, then a linear fade to 0.
	 *
	 * @return 0..1, or 0 once the row is gone.
	 */
	public static float alpha(String questName) {
		Gain gain = GAINS.get(questName);
		if (gain == null) {
			return 0;
		}
		long age = clock.getAsLong() - gain.lastMs();
		if (age < LIFE_MS) {
			return 1f;
		}
		long fading = age - LIFE_MS;
		return fading >= FADE_MS ? 0f : 1f - (fading / (float) FADE_MS);
	}

	public static boolean hasRecent() {
		prune();
		return !GAINS.isEmpty();
	}

	public static void clear() {
		GAINS.clear();
	}
}
