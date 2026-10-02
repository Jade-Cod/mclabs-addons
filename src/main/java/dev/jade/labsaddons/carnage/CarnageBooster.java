package dev.jade.labsaddons.carnage;

import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.hud.Durations;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The global Halloween Carnage Booster, from its {@code Carnage »} broadcasts:
 *
 * <pre>
 * Carnage » Spidrr has just activated a Halloween Carnage Booster!
 * All Carnage Points and Soul of Fright drop rates are boosted by 1.5x for 60 minutes! ...
 *
 * Carnage » A Halloween Carnage Booster is active, sponsored by Spidrr! All Soul of Fright
 * drop chances are boosted 1.5x for 0 minutes. ...
 * </pre>
 *
 * <p>The reminder's minutes are unreliable: four minutes into a 60-minute booster it said 0.
 * So an activation sets the end time (and a second one while running extends it), a reminder
 * re-sets it only when it states more than 0, and a reminder alone (you joined mid-booster)
 * shows the booster as active with no countdown. The server's "boost has ended" line ends it
 * outright.
 */
public final class CarnageBooster {
	/**
	 * A booster known only from reminders is dropped after this long without another.
	 *
	 * <p>ponytail: the reminder cadence is unknown (one came four minutes in); tighten this
	 * once it is.
	 */
	static final long NO_TIMER_STALE_MS = 15 * 60_000L;

	private static final String PREFIX = "Carnage »";
	private static final Pattern RATE = Pattern.compile(
			"boosted\\s+(?:by\\s+)?([\\d.]+)x\\s+for\\s+([^.!]+)", Pattern.CASE_INSENSITIVE);

	/** {@code endMs} 0 means active with no known end. Persisted, so a relog keeps it. */
	public record State(double multiplier, long endMs, long lastSeenMs) {
	}

	private CarnageBooster() {
	}

	public static void onMessage(String text) {
		if (!CarnageEvent.isOn()) {
			return;
		}
		if (ended(text)) {
			clear();
			return;
		}
		State next = next(LabsAddonsConfig.get().carnageBooster, text, System.currentTimeMillis());
		if (next != null) {
			LabsAddonsConfig config = LabsAddonsConfig.get();
			config.carnageBooster = next;
			config.save();
		}
	}

	/** The running booster, or null. */
	public static State active() {
		State state = LabsAddonsConfig.get().carnageBooster;
		return CarnageEvent.isOn() && isActive(state, System.currentTimeMillis()) ? state : null;
	}

	public static void clear() {
		LabsAddonsConfig config = LabsAddonsConfig.get();
		config.carnageBooster = null;
		config.save();
	}

	// --- pure rules ---

	/** The state after this line, or null if the line isn't a booster broadcast. */
	static State next(State current, String text, long nowMs) {
		String line = text.trim();
		if (!line.startsWith(PREFIX)) {
			return null;
		}
		Matcher rate = RATE.matcher(line);
		if (!rate.find()) {
			return null;
		}
		double multiplier = parse(rate.group(1));
		long durationMs = Durations.parseMs(rate.group(2));
		boolean running = isActive(current, nowMs);
		if (line.contains("has just activated")) {
			long from = running && current.endMs() > nowMs ? current.endMs() : nowMs;
			return new State(multiplier, from + durationMs, nowMs);
		}
		if (line.contains("is active")) {
			long end = durationMs > 0 ? nowMs + durationMs : running ? current.endMs() : 0;
			return new State(multiplier, end, nowMs);
		}
		return null;
	}

	/**
	 * "Carnage » Spidrr's Halloween Carnage boost has ended!" The server's real end, which beats
	 * any countdown: a "60 minutes" booster ended after 23 on day one.
	 */
	static boolean ended(String text) {
		String line = text.trim();
		return line.startsWith(PREFIX) && line.contains("Halloween Carnage boost has ended");
	}

	static boolean isActive(State state, long nowMs) {
		if (state == null) {
			return false;
		}
		return state.endMs() > 0 ? nowMs < state.endMs() : nowMs - state.lastSeenMs() < NO_TIMER_STALE_MS;
	}

	private static double parse(String value) {
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			return 1.0;
		}
	}
}
