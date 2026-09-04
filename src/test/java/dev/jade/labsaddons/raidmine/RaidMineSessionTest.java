package dev.jade.labsaddons.raidmine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RaidMineSessionTest {
	private static final long NOW = 1_000_000L;
	private static final int YELLOW = 0xFFFC2E;

	@BeforeEach
	public void reset() {
		RaidMineSession.resetSession();
	}

	private static List<RaidMineGains.Gain> gain(String code, double amount) {
		return List.of(new RaidMineGains.Gain(code, amount, YELLOW));
	}

	@Test
	public void accumulatesRepeatedGainsOfOneResource() {
		RaidMineSession.record(gain("ℯ", 3), NOW);
		RaidMineSession.record(gain("ℯ", 1), NOW + 1_000L);
		RaidMineSession.record(gain("ℯ", 10), NOW + 2_000L);
		assertEquals(14.0, RaidMineSession.rows(NOW + 2_000L).get(0).total());
	}

	@Test
	public void ratesAreMeasuredFromTheFirstGain() {
		// 60 units over 30s of mining is 7200/h, regardless of how long we then idle.
		RaidMineSession.record(gain("ℯ", 60), NOW);
		RaidMineSession.record(gain("ℯ", 0), NOW + 30_000L);
		double perHour = RaidMineSession.rows(NOW + 30_000L).get(0).perHour();
		assertEquals(7200.0, perHour, 0.01);
	}

	@Test
	public void idlingAfterMiningDoesNotDiluteTheRate() {
		RaidMineSession.record(gain("ℯ", 60), NOW);
		RaidMineSession.record(gain("ℯ", 0), NOW + 30_000L);
		double mining = RaidMineSession.rows(NOW + 30_000L).get(0).perHour();
		// An hour later with no further gains, the figure must be unchanged.
		double idle = RaidMineSession.rows(NOW + 30_000L + 3_600_000L).get(0).perHour();
		assertEquals(mining, idle, 0.01);
	}

	@Test
	public void tooShortASampleReportsNoRateRatherThanAWildOne() {
		// One gain, no elapsed time: extrapolating would claim an absurd hourly figure.
		RaidMineSession.record(gain("ℯ", 5), NOW);
		assertEquals(0.0, RaidMineSession.rows(NOW).get(0).perHour());
	}

	@Test
	public void rowsAreBiggestFirst() {
		RaidMineSession.record(gain("𝕊", 2), NOW);
		RaidMineSession.record(gain("ℯ", 40), NOW + 1_000L);
		List<RaidMineSession.Row> rows = RaidMineSession.rows(NOW + 1_000L);
		assertEquals("ℯ", rows.get(0).code());
		assertEquals("𝕊", rows.get(1).code());
	}

	@Test
	public void resetClearsTotalsAndRate() {
		RaidMineSession.record(gain("ℯ", 5), NOW);
		assertTrue(RaidMineSession.hasActivity());
		RaidMineSession.resetSession();
		assertFalse(RaidMineSession.hasActivity());
		assertTrue(RaidMineSession.rows(NOW).isEmpty());
	}

	@Test
	public void multiLineHologramCreditsEveryResourceOnIt() {
		RaidMineSession.record(List.of(
				new RaidMineGains.Gain("ℯ", 3, YELLOW),
				new RaidMineGains.Gain("𝕊", 2, 0x00B1C7)), NOW);
		assertEquals(2, RaidMineSession.rows(NOW).size());
	}
}
