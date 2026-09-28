package dev.jade.labsaddons.gametest;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * Wall time and heap allocation of one hot path, sampled around each call by the timing
 * mixins. Only ever touched from the render thread, so it needs no locking.
 */
public final class Probe {
	public static final Probe HUD = new Probe("HUD render pass");
	public static final Probe CHAT = new Probe("chat line dispatch");
	public static final Probe TICK = new Probe("end-of-tick handlers");
	/** The whole vanilla HUD, the mod's pass included. */
	/** The vanilla HUD up to where the mod's pass starts; see {@code HudTimingMixin}. */
	public static final Probe VANILLA_HUD = new Probe("vanilla HUD (without the mod)");

	private static final Map<String, Probe> WIDGETS = new TreeMap<>();

	private static final com.sun.management.ThreadMXBean THREADS =
			(com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
	// ponytail: fixed sample buffer; a run past it keeps the totals but stops adding to p99.
	private static final int MAX_SAMPLES = 20_000;

	private final String name;
	private final long[] nanos = new long[MAX_SAMPLES];
	private int count;
	private long totalNanos;
	private long totalBytes;
	private long startNanos;
	private long startBytes;
	private boolean running;

	private Probe(String name) {
		this.name = name;
	}

	/** One probe per widget id, made on first use. */
	public static Probe widget(String id) {
		return WIDGETS.computeIfAbsent(id, Probe::new);
	}

	public static Map<String, Probe> widgets() {
		return WIDGETS;
	}

	public void begin() {
		running = true;
		startBytes = THREADS.getCurrentThreadAllocatedBytes();
		startNanos = System.nanoTime();
	}

	/** Ends the current sample; a second end for the same begin is ignored. */
	public void end() {
		if (!running) {
			return;
		}
		running = false;
		long took = System.nanoTime() - startNanos;
		totalBytes += THREADS.getCurrentThreadAllocatedBytes() - startBytes;
		totalNanos += took;
		if (count < MAX_SAMPLES) {
			nanos[count] = took;
		}
		count++;
	}

	public void reset() {
		count = 0;
		totalNanos = 0;
		totalBytes = 0;
	}

	public int count() {
		return count;
	}

	public double meanMicros() {
		return count == 0 ? 0 : totalNanos / 1_000.0 / count;
	}

	public double p99Micros() {
		int n = Math.min(count, MAX_SAMPLES);
		if (n == 0) {
			return 0;
		}
		long[] sorted = Arrays.copyOf(nanos, n);
		Arrays.sort(sorted);
		return sorted[(int) Math.min(n - 1, Math.ceil(n * 0.99) - 1)] / 1_000.0;
	}

	public double bytesPerCall() {
		return count == 0 ? 0 : (double) totalBytes / count;
	}

	public String summary() {
		return String.format("%s: %d calls, mean %.1f us, p99 %.1f us, %.0f bytes/call",
				name, count, meanMicros(), p99Micros(), bytesPerCall());
	}
}
