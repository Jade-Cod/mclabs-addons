package dev.jade.labsaddons.double2;

import java.util.List;
import java.util.Map;

/**
 * One frame's reading of the Double² menu. Everything here came off the container the
 * server sent; nothing is remembered or inferred, so a stale value cannot survive into
 * the next round.
 *
 * @param window the nine panes of the strip, slot 45 first; any may be null if the
 *               server has not filled that slot yet
 */
public record D2State(Phase phase, int secondsLeft, Lab selected, long stake,
		Lab betLab, long betAmount, Map<Lab, Long> pot, int investors,
		List<Lab> window, Lab settledLab, int eolDrought) {

	public enum Phase {
		/** Investing open, the clock running down. */
		BETTING,
		/** Market closed, the wheel turning. */
		SPINNING,
		/** The wheel has stopped and the banner names the profiting lab. */
		SETTLED
	}

	/**
	 * The lab under the pointer. Read straight off slot 49 — the wheel's order is
	 * learned and may not be known yet, but the segment at the pointer always is.
	 */
	public Lab pointerLab() {
		return window.size() > D2Ring.POINTER ? window.get(D2Ring.POINTER) : null;
	}

	public long potTotal() {
		long total = 0;
		for (long amount : pot.values()) {
			total += amount;
		}
		return total;
	}

	public boolean hasBet() {
		return betLab != null && betAmount > 0;
	}
}
