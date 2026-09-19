package dev.jade.labsaddons.double2;

import java.util.Map;

/**
 * One frame's reading of the Double² menu. Everything here came off the container the
 * server sent; nothing is remembered or inferred, so a stale value cannot survive into
 * the next round.
 *
 * @param ringOffset where the visible window sits on the ring, or -1 if it could not
 *                   be located — the overlay then draws no wheel rather than a wrong one.
 */
public record D2State(Phase phase, int secondsLeft, Lab selected, long stake,
		Lab betLab, long betAmount, Map<Lab, Long> pot, int investors,
		int ringOffset, Lab settledLab, int eolDrought) {

	public enum Phase {
		/** Investing open, the clock running down. */
		BETTING,
		/** Market closed, the wheel turning. */
		SPINNING,
		/** The wheel has stopped and the banner names the profiting lab. */
		SETTLED
	}

	/** The lab currently under the pointer, or null when the ring isn't located. */
	public Lab pointerLab() {
		return ringOffset < 0 ? null : D2Ring.pointerLab(ringOffset);
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
