package dev.jade.labsaddons.double2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class D2RingTest {
	/** A wheel of the right shape but a different order from the seed. */
	private static final Lab[] RESHUFFLED =
			("CQL ADL CQL MSL CQL MSL CQL RDL CQL ADL CQL MSL CQL EOL CQL ADL CQL MSL "
					+ "CQL RDL CQL MSL CQL ADL MSL").split(" ").length == D2Ring.SIZE
					? labs("CQL ADL CQL MSL CQL MSL CQL RDL CQL ADL CQL MSL CQL EOL CQL ADL "
							+ "CQL MSL CQL RDL CQL MSL CQL ADL MSL")
					: null;

	private static Lab[] labs(String codes) {
		String[] parts = codes.split(" ");
		Lab[] out = new Lab[parts.length];
		for (int i = 0; i < parts.length; i++) {
			out[i] = Lab.valueOf(parts[i]);
		}
		return out;
	}

	private static List<Lab> windowOf(Lab[] ring, int start) {
		List<Lab> out = new ArrayList<>(D2Ring.WINDOW);
		for (int i = 0; i < D2Ring.WINDOW; i++) {
			out.add(ring[Math.floorMod(start + i, ring.length)]);
		}
		return out;
	}

	/** Turns the wheel past the strip, as the server does during a spin. */
	private static void spin(Lab[] ring, int from, int steps) {
		for (int step = 0; step <= steps; step++) {
			D2Ring.observe(windowOf(ring, from + step));
		}
	}

	@BeforeEach
	void forget() {
		D2Ring.reset();
	}

	@Test
	void adoptsTheSeedWhenItExplainsTheStrip() {
		// Round A's final strip, captured 2026-09-18.
		int offset = D2Ring.observe(windowOf(D2Ring.seed(), 0));
		assertEquals(0, offset);
		assertTrue(D2Ring.isComplete(), "a strip the seed explains gives the whole wheel at once");
		assertArrayEquals(D2Ring.seed(), D2Ring.segments());
	}

	@Test
	void knowsOnlyTheStripUntilTheWheelTurns() {
		D2Ring.observe(windowOf(RESHUFFLED, 3));
		assertFalse(D2Ring.isComplete());
		long known = Arrays.stream(D2Ring.segments()).filter(java.util.Objects::nonNull).count();
		assertEquals(D2Ring.WINDOW, known, "only the nine on screen are known");
	}

	@Test
	void learnsTheWholeWheelFromOneSpin() {
		// Sixteen steps brings the far side of the wheel past the strip.
		spin(RESHUFFLED, 0, D2Ring.SIZE - D2Ring.WINDOW);
		assertTrue(D2Ring.isComplete(), "a full turn should reveal every segment");
		// Learned in some rotation of the real thing, so check it explains any strip.
		for (int start = 0; start < D2Ring.SIZE; start++) {
			int offset = D2Ring.observe(windowOf(RESHUFFLED, start));
			assertNotEquals(-1, offset);
			assertEquals(windowOf(RESHUFFLED, start), windowOf(D2Ring.segments(), offset));
		}
	}

	@Test
	void followsTheWheelAsItTurns() {
		spin(RESHUFFLED, 0, D2Ring.SIZE);
		int previous = D2Ring.observe(windowOf(RESHUFFLED, 5));
		int next = D2Ring.observe(windowOf(RESHUFFLED, 6));
		assertEquals(Math.floorMod(previous + 1, D2Ring.SIZE), next);
	}

	/** Opening the menu again can start the wheel anywhere; that is a rotation, not a change. */
	@Test
	void aRandomStartPositionIsNotANewWheel() {
		spin(RESHUFFLED, 0, D2Ring.SIZE);
		Lab[] learned = D2Ring.segments();
		D2Ring.observe(windowOf(RESHUFFLED, 17));
		assertArrayEquals(learned, D2Ring.segments(), "a jump should not throw the wheel away");
	}

	/** But a genuinely different order must be, or the board draws a wheel that is a lie. */
	@Test
	void aReshuffleIsThrownAwayAndRelearned() {
		D2Ring.observe(windowOf(D2Ring.seed(), 0));
		assertTrue(D2Ring.isComplete());

		spin(RESHUFFLED, 0, D2Ring.SIZE - D2Ring.WINDOW);
		assertTrue(D2Ring.isComplete());
		int offset = D2Ring.observe(windowOf(RESHUFFLED, 4));
		assertEquals(windowOf(RESHUFFLED, 4), windowOf(D2Ring.segments(), offset));
	}

	@Test
	void ignoresAStripItCannotUse() {
		assertEquals(-1, D2Ring.observe(null));
		assertEquals(-1, D2Ring.observe(List.of()));
		assertEquals(-1, D2Ring.observe(Arrays.asList(Lab.CQL, Lab.MSL, Lab.CQL, Lab.ADL,
				Lab.CQL, Lab.ADL, Lab.MSL, Lab.CQL, null)));
	}

	@Test
	void theSeedStillHasTheShapeTheCapturesShowed() {
		int[] counts = new int[Lab.values().length];
		for (Lab lab : D2Ring.seed()) {
			counts[lab.ordinal()]++;
		}
		assertEquals(12, counts[Lab.CQL.ordinal()]);
		assertEquals(6, counts[Lab.MSL.ordinal()]);
		assertEquals(4, counts[Lab.ADL.ordinal()]);
		assertEquals(2, counts[Lab.RDL.ordinal()]);
		assertEquals(1, counts[Lab.EOL.ordinal()]);
	}
}
