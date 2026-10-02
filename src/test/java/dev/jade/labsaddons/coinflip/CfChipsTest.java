package dev.jade.labsaddons.coinflip;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CfChipsTest {
	@Test
	public void chipsAddUpAndStopAtTheMax() {
		assertEquals(2_500_000, CfChips.add(CfChips.add(500_000, 1_000_000), 1_000_000));
		assertEquals(CfChips.MAX, CfChips.add(99_000_000, 10_000_000));
	}

	@Test
	public void labelsAreShort() {
		assertEquals("500k", CfChips.label(500_000));
		assertEquals("10m", CfChips.label(10_000_000));
		assertEquals("2.5m", CfChips.label(2_500_000));
		assertEquals("750", CfChips.label(750));
	}

	@Test
	public void limitsAre200To100m() {
		assertTrue(CfChips.valid(200));
		assertTrue(CfChips.valid(100_000_000));
		assertFalse(CfChips.valid(199));
		assertFalse(CfChips.valid(100_000_001));
	}

	@Test
	public void aBadSavedSetFallsBackToTheDefaults() {
		assertEquals(CfChips.DEFAULTS, CfChips.sanitize(List.of(1L, 2L)));
		assertEquals(CfChips.DEFAULTS, CfChips.sanitize(List.of(500L, 1_000L, 5L, 10_000L)));
		assertEquals(List.of(1_000L, 2_000L, 3_000L, 4_000L), CfChips.sanitize(List.of(1_000L, 2_000L, 3_000L, 4_000L)));
	}
}
