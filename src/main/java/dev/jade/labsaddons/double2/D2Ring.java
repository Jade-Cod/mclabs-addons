package dev.jade.labsaddons.double2;

import java.util.Arrays;
import java.util.List;

/**
 * The Double² wheel, learned from the strip rather than assumed.
 *
 * <p>The server shows nine of the wheel's 25 segments at a time, in slots 45–53, and
 * slides them one place per tick while it spins. Watching that fills the other sixteen
 * in: sixteen ticks of a spin is a whole turn of the wheel. Until then the unseen part
 * is simply unknown, and says so, rather than being guessed at.
 *
 * <p>An earlier version hardcoded the order, derived from two captured rounds. It held
 * for those rounds and then stopped, so the order is not a constant — it survives here
 * only as {@link #SEED}, a candidate checked against the live strip like any other and
 * dropped the moment it disagrees. Anything that no longer matches is thrown away and
 * relearned, which covers the wheel being reshuffled as well as merely restarted
 * somewhere new.
 */
public final class D2Ring {
	public static final int SIZE = 25;
	/** Container slots 45–53: the slice of the wheel the server draws. */
	public static final int WINDOW = 9;
	/** Index within that window sitting under the pointer — container slot 49. */
	public static final int POINTER = 4;
	/** Most the wheel could have moved between two frames before we stop believing it. */
	private static final int MAX_STEP = WINDOW;
	/**
	 * How far ahead a torn frame is looked for, and equally how far a clean fit is taken at
	 * face value. The wheel advances one segment per server tick, so one or two is the
	 * ordinary case and three covers a dropped frame; past that a fit found among segments
	 * still unknown is coincidence rather than evidence.
	 */
	private static final int NEAR_STEP = 3;

	/** The order first derived, kept only as a candidate; see the class note. */
	private static final Lab[] SEED = {
			Lab.CQL, Lab.MSL, Lab.CQL, Lab.ADL, Lab.CQL, Lab.ADL, Lab.MSL, Lab.CQL, Lab.RDL,
			Lab.CQL, Lab.MSL, Lab.CQL, Lab.ADL, Lab.CQL, Lab.MSL, Lab.CQL, Lab.EOL, Lab.CQL,
			Lab.MSL, Lab.CQL, Lab.ADL, Lab.CQL, Lab.MSL, Lab.CQL, Lab.RDL
	};

	/** What is known of the wheel; a null is a segment not yet seen. */
	private static Lab[] segments = new Lab[SIZE];
	/** Where the visible window starts, in {@link #segments} coordinates. */
	private static int offset = -1;

	private D2Ring() {
	}

	/**
	 * Takes the strip as it stands and works out where the wheel is.
	 *
	 * @return the position the window starts at, or -1 if the strip is unusable
	 */
	public static int observe(List<Lab> window) {
		if (window == null || window.size() != WINDOW || window.contains(null)) {
			return -1;
		}
		if (offset < 0) {
			int placed = placeWindow(window);
			if (placed >= 0) {
				offset = placed;
				write(window);
				return offset;
			}
			start(window);
			return offset;
		}

		// A torn frame arrives when slot packets are only partially received during a spin.
		// Hold the last position rather than mistaking it for a step or a reshuffle.
		if (isTornFrame(window, offset)) {
			return -1;
		}

		// The wheel only ever turns one way, so look forwards from where it was. Nothing
		// beyond a window's worth can be believed: the pattern is half one lab, and a
		// longer reach would find a false match sooner than the true one.
		for (int step = 0; step <= MAX_STEP; step++) {
			if (fits(window, offset + step)) {
				offset = Math.floorMod(offset + step, SIZE);
				write(window);
				return offset;
			}
		}
		// Beyond a turn's reach, but reopening the menu starts the wheel wherever it
		// likes, and a jump is still the same wheel. Accept one if it lands in exactly
		// one place; an incomplete wheel usually fits several, and a guess there would
		// draw segments that are not there.
		int jumped = sole(window);
		if (jumped >= 0) {
			offset = jumped;
			write(window);
			return offset;
		}
		// While still learning the wheel, an unplaced frame is an ambiguous or torn
		// frame: hold the last good position rather than throwing away learned segments.
		if (!isComplete()) {
			return -1;
		}
		// The wheel was complete and fits nowhere at all, so this is no longer the same wheel.
		start(window);
		return offset;
	}

	/** Everything known of the wheel, nulls and all, for drawing. */
	public static Lab[] segments() {
		return segments.clone();
	}

	/** Whether every segment has been seen, so the whole wheel can be drawn. */
	public static boolean isComplete() {
		for (Lab lab : segments) {
			if (lab == null) {
				return false;
			}
		}
		return true;
	}

	/** Reset offset only, preserving learned segments across rounds and menu reopens. */
	public static void resetOffset() {
		offset = -1;
	}

	/** Forget the wheel — a different container, or a different server. */
	public static void reset() {
		segments = new Lab[SIZE];
		offset = -1;
	}

	/**
	 * Detects a torn network frame where the client renders while slots 45-53 are
	 * partially updated between {@code atOffset} and a forward step.
	 */
	private static boolean isTornFrame(List<Lab> window, int atOffset) {
		if (atOffset < 0) {
			return false;
		}
		// A window that simply fits a step the wheel could have taken is the wheel having
		// taken it. Asking that first is what keeps an ordinary step from reading as torn:
		// the tail test below counts an unknown segment as a match, so it can be satisfied
		// by coincidence — and it is, whenever the ring holds two identical labs side by
		// side, which only the alternating orders captured so far happen not to. Measured
		// on a ring with one such pair: four frames a revolution held and then jumped two
		// segments.
		for (int step = 0; step <= NEAR_STEP; step++) {
			if (fits(window, atOffset + step)) {
				return false;
			}
		}
		for (int step = 1; step <= NEAR_STEP; step++) {
			int nextOffset = Math.floorMod(atOffset + step, SIZE);
			for (int k = 1; k < WINDOW; k++) {
				boolean matchesNew = true;
				for (int i = 0; i < k; i++) {
					Lab known = segments[Math.floorMod(nextOffset + i, SIZE)];
					if (known != null && known != window.get(i)) {
						matchesNew = false;
						break;
					}
				}
				if (!matchesNew) {
					continue;
				}
				boolean matchesOld = true;
				for (int i = k; i < WINDOW; i++) {
					Lab known = segments[Math.floorMod(atOffset + i, SIZE)];
					if (known != null && known != window.get(i)) {
						matchesOld = false;
						break;
					}
				}
				if (matchesOld) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Attempts to place the window against already-known segments, requiring that the match
	 * be unique and agree with known segments.
	 */
	private static int placeWindow(List<Lab> window) {
		int bestStart = -1;
		int maxKnown = 0;
		int matchesFound = 0;

		for (int start = 0; start < SIZE; start++) {
			if (fits(window, start)) {
				int known = countMatchingKnown(window, start);
				if (known > maxKnown) {
					maxKnown = known;
					bestStart = start;
					matchesFound = 1;
				} else if (known == maxKnown && known > 0) {
					matchesFound++;
				}
			}
		}

		if (matchesFound == 1 && maxKnown >= (isComplete() ? WINDOW : 3)) {
			return bestStart;
		}
		return -1;
	}

	private static int countMatchingKnown(List<Lab> window, int at) {
		int count = 0;
		for (int i = 0; i < WINDOW; i++) {
			Lab known = segments[Math.floorMod(at + i, SIZE)];
			if (known != null && known == window.get(i)) {
				count++;
			}
		}
		return count;
	}

	/** Begin again from this strip, adopting the seed when it happens to explain it. */
	private static void start(List<Lab> window) {
		int seeded = find(SEED, window);
		if (seeded >= 0) {
			segments = SEED.clone();
			offset = seeded;
			return;
		}
		segments = new Lab[SIZE];
		offset = 0;
		write(window);
	}

	/** The one position on the known wheel the window fits, or -1 if it is not the one. */
	private static int sole(List<Lab> window) {
		int found = -1;
		for (int start = 0; start < SIZE; start++) {
			if (!fits(window, start)) {
				continue;
			}
			if (found >= 0) {
				return -1;
			}
			found = start;
		}
		return found;
	}

	/** Whether the window agrees with every segment already known at that position. */
	private static boolean fits(List<Lab> window, int at) {
		for (int i = 0; i < WINDOW; i++) {
			Lab known = segments[Math.floorMod(at + i, SIZE)];
			if (known != null && known != window.get(i)) {
				return false;
			}
		}
		return true;
	}

	/** Records the window at the current offset, filling in whatever was unknown. */
	private static void write(List<Lab> window) {
		for (int i = 0; i < WINDOW; i++) {
			segments[Math.floorMod(offset + i, SIZE)] = window.get(i);
		}
	}

	/** The one position of {@code ring} that explains the window, or -1. */
	private static int find(Lab[] ring, List<Lab> window) {
		int found = -1;
		for (int start = 0; start < SIZE; start++) {
			boolean match = true;
			for (int i = 0; i < WINDOW; i++) {
				if (ring[Math.floorMod(start + i, SIZE)] != window.get(i)) {
					match = false;
					break;
				}
			}
			if (!match) {
				continue;
			}
			if (found >= 0) {
				// Two positions fit, so the window does not name one. Not possible on a
				// wheel of this shape, but never guess if it ever becomes so.
				return -1;
			}
			found = start;
		}
		return found;
	}

	/** Visible for testing: the order first derived from the captures. */
	static Lab[] seed() {
		return SEED.clone();
	}

	/** Visible for testing. */
	static int offset() {
		return offset;
	}

	@Override
	public String toString() {
		return Arrays.toString(segments);
	}
}
