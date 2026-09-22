package dev.jade.labsaddons.coinflip;

/**
 * The shape of the toss.
 *
 * <p>The mod spins its own coin rather than following the server's nineteen head swaps,
 * because those swaps end on the wrong face: the last one shown before the result is not
 * the winner. So the curve here is the server's cadence, measured off the capture —
 * eight half-turns at 150 ms, six at 300, four at 450, one at 600 — and then it stops.
 *
 * <p>That last half-turn is never completed on the clock. Past 5.4 s the coin creeps toward
 * edge-on and hangs there, standing on its rim, until the server says who won. Only then
 * does it fall, onto the winner's face. Six seconds of the server's own timing, and the
 * mod never has to guess an outcome it has not been told.
 */
public final class CfToss {
	/** Half-turns in each phase of the toss, and how long each of them takes. */
	private static final int[] TURNS = {8, 6, 4, 1};
	private static final long[] TURN_MS = {150L, 300L, 450L, 600L};
	/** Where the creep starts: 19 half-turns, 5,400 ms in. */
	public static final float HOLD_AT = 19f;
	public static final long HOLD_MS = 5_400L;
	/**
	 * How far into that last half-turn the creep may get. Short of the half-way point, so
	 * the coin settles on its edge instead of falling onto a face nobody has chosen.
	 */
	private static final float HOLD_CREEP = 0.45f;
	private static final long HOLD_TAU_MS = 900L;
	/**
	 * How far the coin rocks while it waits. A coin standing perfectly still on its rim
	 * reads as a frozen screen; one rocking reads as a coin that has not decided. Small
	 * enough that the creep plus the rock never reaches the half-turn.
	 */
	private static final float WOBBLE = 0.035f;
	private static final double WOBBLE_MS = 260.0;
	/** How long the fall takes once the winner is known. */
	public static final long LAND_MS = 260L;
	/** Past this with no result, something is wrong and the board says so. */
	public static final long STALLED_MS = 9_000L;

	private CfToss() {
	}

	/** Half-turns completed by {@code elapsedMs}, creeping toward the hold after 5.4 s. */
	public static float halfTurns(long elapsedMs) {
		float turns = 0f;
		long left = Math.max(0L, elapsedMs);
		for (int phase = 0; phase < TURNS.length; phase++) {
			long span = TURNS[phase] * TURN_MS[phase];
			if (left < span) {
				return turns + left / (float) TURN_MS[phase];
			}
			turns += TURNS[phase];
			left -= span;
		}
		return turns + HOLD_CREEP * (1f - (float) Math.exp(-left / (double) HOLD_TAU_MS))
				+ WOBBLE * (float) Math.sin(left / WOBBLE_MS);
	}

	/** 1 face-on, 0 edge-on. */
	public static float squeeze(float halfTurns) {
		return (float) Math.abs(Math.cos(Math.PI * halfTurns));
	}

	/** Which face is up: 0 for the near side of the coin, 1 for the far one. */
	public static int faceUp(float halfTurns) {
		int steps = (int) Math.floor(halfTurns + 0.5f);
		return ((steps % 2) + 2) % 2;
	}

	/**
	 * Where the coin has to stop to show {@code face}: the next half-turn boundary of the
	 * right parity, at least half a turn away so the fall is visible.
	 */
	public static float landingTarget(float from, int face) {
		int target = (int) Math.ceil(from + 0.5f);
		if (((target % 2) + 2) % 2 != face) {
			target++;
		}
		return target;
	}

	/** How high the coin is, 0 on the table and 1 at the top of the toss. */
	public static float lift(long elapsedMs) {
		float progress = Math.clamp(elapsedMs / (float) HOLD_MS, 0f, 1f);
		return 4f * progress * (1f - progress);
	}

	/**
	 * The squash as the coin hits: flattened at the moment of impact, overshooting once,
	 * then still. Returned as a vertical scale, so it goes above 1 on the stretch.
	 *
	 * @param sinceMs time since the fall finished
	 */
	public static float settle(long sinceMs) {
		if (sinceMs < 0L) {
			return 1f;
		}
		double decay = Math.exp(-sinceMs / 95.0);
		return Math.clamp((float) (1.0 - 0.30 * decay * Math.cos(sinceMs / 40.0)), 0.55f,
				1.35f);
	}

	/** Ease-out cubic, for the fall. */
	public static float ease(float t) {
		float left = 1f - Math.clamp(t, 0f, 1f);
		return 1f - left * left * left;
	}
}
