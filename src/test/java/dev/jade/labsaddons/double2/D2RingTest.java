package dev.jade.labsaddons.double2;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class D2RingTest {
	private static List<Lab> window(String codes) {
		List<Lab> out = new ArrayList<>();
		for (String code : codes.split(" ")) {
			out.add(Lab.valueOf(code));
		}
		return out;
	}

	@Test
	void everyNineWindowIsUnique() {
		Set<List<Lab>> seen = new HashSet<>();
		for (int start = 0; start < D2Ring.SIZE; start++) {
			List<Lab> slice = new ArrayList<>();
			for (int i = 0; i < D2Ring.WINDOW; i++) {
				slice.add(D2Ring.at(start + i));
			}
			assertEquals(true, seen.add(slice), "two ring positions share a nine-window at " + start);
		}
		assertEquals(D2Ring.SIZE, seen.size());
	}

	/**
	 * Eight is not enough, which is why the window the server sends has no slack in it.
	 * If this ever starts passing, the ring changed and {@link D2Ring#lockOn} is guessing.
	 */
	@Test
	void eightWideWouldBeAmbiguous() {
		Set<List<Lab>> seen = new HashSet<>();
		for (int start = 0; start < D2Ring.SIZE; start++) {
			List<Lab> slice = new ArrayList<>();
			for (int i = 0; i < 8; i++) {
				slice.add(D2Ring.at(start + i));
			}
			seen.add(slice);
		}
		assertNotEquals(D2Ring.SIZE, seen.size(), "an eight-wide window is no longer ambiguous");
	}

	@Test
	void lockOnFindsEveryPosition() {
		for (int start = 0; start < D2Ring.SIZE; start++) {
			List<Lab> slice = new ArrayList<>();
			for (int i = 0; i < D2Ring.WINDOW; i++) {
				slice.add(D2Ring.at(start + i));
			}
			assertEquals(start, D2Ring.lockOn(slice));
		}
	}

	@Test
	void lockOnRefusesRubbish() {
		assertEquals(-1, D2Ring.lockOn(null));
		assertEquals(-1, D2Ring.lockOn(List.of()));
		assertEquals(-1, D2Ring.lockOn(window("CQL MSL CQL ADL CQL ADL MSL CQL")));
		assertEquals(-1, D2Ring.lockOn(Arrays.asList(Lab.CQL, Lab.MSL, Lab.CQL, Lab.ADL,
				Lab.CQL, Lab.ADL, Lab.MSL, Lab.CQL, null)));
		// Five EOL in a row is not anywhere on this wheel.
		assertEquals(-1, D2Ring.lockOn(window("EOL EOL EOL EOL EOL EOL EOL EOL EOL")));
	}

	/**
	 * The two rounds captured on 2026-09-18. Each final strip is what slots 45–53 held when
	 * the wheel stopped, and chat then announced the lab below — so this pins both the ring
	 * order and that slot 49 is the pointer, against real server output.
	 */
	@Test
	void capturedRoundsLandOnTheAnnouncedLab() {
		assertEquals(Lab.ADL, pointerFor("RDL CQL MSL CQL ADL CQL ADL MSL CQL"),
				"round A announced \"The profiting lab is ADL!\"");
		assertEquals(Lab.CQL, pointerFor("CQL EOL CQL MSL CQL ADL CQL MSL CQL"),
				"round B announced \"The profiting lab is CQL!\"");
	}

	private static Lab pointerFor(String strip) {
		int offset = D2Ring.lockOn(window(strip));
		assertNotEquals(-1, offset, "captured strip did not lock onto the ring: " + strip);
		return D2Ring.pointerLab(offset);
	}

	@Test
	void upcomingStartsAfterThePointer() {
		int offset = D2Ring.lockOn(window("RDL CQL MSL CQL ADL CQL ADL MSL CQL"));
		// Slot 50 onward: the strip scrolls towards 45, so slot 50 reaches the pointer next.
		assertEquals(List.of(Lab.CQL, Lab.ADL, Lab.MSL, Lab.CQL), D2Ring.upcoming(offset, 4));
		assertEquals(4, D2Ring.visibleAhead());
	}

	@Test
	void ringCompositionIsTwelveSixFourTwoOne() {
		int[] counts = new int[Lab.values().length];
		for (int i = 0; i < D2Ring.SIZE; i++) {
			counts[D2Ring.at(i).ordinal()]++;
		}
		assertEquals(12, counts[Lab.CQL.ordinal()]);
		assertEquals(6, counts[Lab.MSL.ordinal()]);
		assertEquals(4, counts[Lab.ADL.ordinal()]);
		assertEquals(2, counts[Lab.RDL.ordinal()]);
		assertEquals(1, counts[Lab.EOL.ordinal()]);
	}
}
