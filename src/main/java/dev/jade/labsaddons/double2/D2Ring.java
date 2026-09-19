package dev.jade.labsaddons.double2;

import java.util.ArrayList;
import java.util.List;

/**
 * The Double² wheel: one fixed ring of 25 segments.
 *
 * <p>Fixed is the load-bearing word. Two captured rounds produced sequences that were
 * exact rotations of one another, so the order never changes — only where it stops.
 * That is what lets the overlay draw the whole wheel from the nine segments the
 * server shows, and say what is coming next.
 *
 * <p>The nine-wide window the server sends is exactly enough to locate it: all 25
 * nine-windows are distinct, where an eight-wide one would collide. {@link #lockOn}
 * relies on that and refuses to guess if it ever stops being true.
 */
public final class D2Ring {
	private static final Lab[] RING = {
			Lab.CQL, Lab.MSL, Lab.CQL, Lab.ADL, Lab.CQL, Lab.ADL, Lab.MSL, Lab.CQL, Lab.RDL,
			Lab.CQL, Lab.MSL, Lab.CQL, Lab.ADL, Lab.CQL, Lab.MSL, Lab.CQL, Lab.EOL, Lab.CQL,
			Lab.MSL, Lab.CQL, Lab.ADL, Lab.CQL, Lab.MSL, Lab.CQL, Lab.RDL
	};

	public static final int SIZE = RING.length;
	/** Container slots 45–53: the slice of the ring the server draws. */
	public static final int WINDOW = 9;
	/** Index within that window that sits under the pointer — container slot 49. */
	public static final int POINTER = 4;

	private D2Ring() {
	}

	public static Lab at(int index) {
		return RING[Math.floorMod(index, SIZE)];
	}

	/**
	 * @return the ring index the window's first segment sits at, or -1 when the window
	 *         is incomplete or matches no single position.
	 */
	public static int lockOn(List<Lab> window) {
		if (window == null || window.size() != WINDOW || window.contains(null)) {
			return -1;
		}
		int found = -1;
		for (int start = 0; start < SIZE; start++) {
			boolean match = true;
			for (int i = 0; i < WINDOW; i++) {
				if (at(start + i) != window.get(i)) {
					match = false;
					break;
				}
			}
			if (!match) {
				continue;
			}
			if (found >= 0) {
				// Two positions fit. Impossible on this ring, so the ring has changed
				// under us — better to draw nothing than to draw a lie.
				return -1;
			}
			found = start;
		}
		return found;
	}

	/** The lab under the pointer, for a window starting at {@code offset}. */
	public static Lab pointerLab(int offset) {
		return at(offset + POINTER);
	}

	/**
	 * The next segments due at the pointer, nearest first. Segments travel from slot 53
	 * towards slot 45, so the one after the pointer is the next to arrive — and only
	 * four of these are on screen. The rest is why knowing the ring is worth anything.
	 */
	public static List<Lab> upcoming(int offset, int count) {
		List<Lab> out = new ArrayList<>(count);
		for (int i = 1; i <= count; i++) {
			out.add(at(offset + POINTER + i));
		}
		return out;
	}

	/** How many of {@link #upcoming} the server itself is already showing. */
	public static int visibleAhead() {
		return WINDOW - POINTER - 1;
	}
}
