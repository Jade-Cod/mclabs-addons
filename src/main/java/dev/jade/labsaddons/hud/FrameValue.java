package dev.jade.labsaddons.hud;

import java.util.function.Supplier;

/**
 * One value, built at most once a frame.
 *
 * <p>A widget's rows get asked for three or four times per frame and never change in between:
 * {@link HudObject} measures the content's width, then its height, then draws it, and
 * {@code shouldRender} may ask a fourth time before any of that. Each of those calls was
 * rebuilding the list from scratch — copying the tracker's backing collection, sorting it,
 * formatting every figure — for an answer identical to the one the previous call had just
 * worked out.
 *
 * <p>A profile put the two worst offenders at 236 ms and 72 ms of a 17-second render thread.
 * The samples landed on {@code String.format} and {@code String.toLowerCase}, but those are
 * only the innermost thing being repeated; the repetition is the whole builder, so that is
 * what this stops.
 *
 * <p>The frame is identified by the millisecond, which is the granularity available without
 * threading a frame counter through every widget. Two frames sharing a millisecond would need
 * a thousand of them a second, and a frame straddling two costs one extra build — so the worst
 * case is the behaviour we already had, for one frame.
 *
 * <p>Minecraft-free on purpose: the clock is the caller's, like every other reader in this mod,
 * so the whole thing can be driven through a test without a game.
 *
 * <p>The value is handed out as the builder made it and is <em>not</em> copied, so a caller
 * must treat it as read-only — every one of them only measures or draws it.
 */
public final class FrameValue<T> {
	private long builtAtMs = Long.MIN_VALUE;
	private boolean builtPreview;
	private T value;
	private int builds;

	/**
	 * The value for this frame, building it if this is the first ask.
	 *
	 * @param preview kept as part of the key: the editor draws a preview with different
	 *                contents, and a widget that measured one and then drew the other would
	 *                lay itself out to the wrong size
	 */
	public T get(long nowMs, boolean preview, Supplier<T> build) {
		if (value == null || nowMs != builtAtMs || preview != builtPreview) {
			value = build.get();
			builtAtMs = nowMs;
			builtPreview = preview;
			builds++;
		}
		return value;
	}

	/** Visible for testing: how many times the builder has actually run. */
	int builds() {
		return builds;
	}
}
