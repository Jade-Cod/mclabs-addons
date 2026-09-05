package dev.jade.labsaddons.hud.editor;

/**
 * A 0..1 roll that eases out and reverses from wherever it currently is, rather than
 * snapping. The Runner Leaderboard's job-history accordion and the HUD editor's Widgets
 * rail both animate this way; sharing the class is what keeps them feeling the same.
 *
 * <p>Callers draw their content at the offsets it would occupy fully open and clip to
 * {@code progress()} of the full size — the clip is what does the rolling.
 */
public final class Shutter {
	private final long durationMs;
	private double from;
	private double target;
	private long startMs;

	public Shutter(double initial, long durationMs, long now) {
		this.durationMs = durationMs;
		this.from = initial;
		this.target = initial;
		this.startMs = now;
	}

	public float progress() {
		float t = Math.min(1f, (System.currentTimeMillis() - startMs) / (float) durationMs);
		float eased = 1f - (1f - t) * (1f - t);
		return (float) (from + (target - from) * eased);
	}

	public double target() {
		return target;
	}

	/**
	 * Points the roll at {@code newTarget}. Captures the current instantaneous progress
	 * first, so reversing mid-roll eases from where it actually was.
	 */
	public void retarget(double newTarget, long now) {
		if (target != newTarget) {
			from = progress();
			startMs = now;
			target = newTarget;
		}
	}

	/** True once the roll has finished (used to retire fully closed shutters). */
	public boolean isSettled(long now) {
		return now - startMs >= durationMs;
	}
}
