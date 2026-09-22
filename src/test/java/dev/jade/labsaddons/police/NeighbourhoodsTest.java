package dev.jade.labsaddons.police;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bundled Dynmap outlines, spot-checked against points inside each region. */
public class NeighbourhoodsTest {
	@Test
	public void eachRegionHoldsItsOwnGround() {
		assertTrue(Neighbourhoods.contains("red", -319, 167));
		assertTrue(Neighbourhoods.contains("blue", -483, -386));
		assertTrue(Neighbourhoods.contains("prison", -227, 137));
		assertTrue(Neighbourhoods.contains("green", -681, -9));
	}

	@Test
	public void yellowNestsInsideGreen() {
		// Walking in raises both boss bars, and the outlines agree: Yellow's polygon is
		// wholly within Green's, so an arrest there counts for both patrols.
		assertTrue(Neighbourhoods.contains("yellow", -674, -59));
		assertTrue(Neighbourhoods.contains("green", -674, -59));
		// The rest of Green is Green alone.
		assertFalse(Neighbourhoods.contains("yellow", -681, -9));
	}

	@Test
	public void groundOutsideEveryRegionBelongsToNoOne() {
		// The spawn point itself sits between the neighbourhoods.
		for (String region : new String[] {"red", "blue", "green", "yellow", "pink", "orange", "purple", "prison"}) {
			assertFalse(Neighbourhoods.contains(region, -381, -123), region + " should not hold spawn");
			assertFalse(Neighbourhoods.contains(region, 0, 0), region + " should not hold the origin");
		}
	}

	@Test
	public void anUnknownRegionHoldsNothing() {
		assertFalse(Neighbourhoods.contains("teal", -319, 167));
	}
}
