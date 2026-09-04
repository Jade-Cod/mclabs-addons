package dev.jade.labsaddons.raidmine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RaidMineGainsTest {
	private static final int YELLOW = 0xFFFC2E;
	private static final int DARK_CYAN = 0x00B1C7;
	private static final int BLUE = 0x42A5F5;

	/** The component shape the server actually sends: "+", amount, then the code. */
	private static List<RaidMineGains.Segment> holo(Object... parts) {
		return java.util.stream.IntStream.range(0, parts.length / 2)
				.mapToObj(i -> new RaidMineGains.Segment(
						(String) parts[i * 2], (Integer) parts[i * 2 + 1]))
				.toList();
	}

	@Test
	public void readsASingleGain() {
		List<RaidMineGains.Gain> gains = RaidMineGains.parse(
				holo("+", YELLOW, "3", YELLOW, "ℯ", YELLOW));
		assertEquals(1, gains.size());
		assertEquals("ℯ", gains.get(0).code());
		assertEquals(3.0, gains.get(0).amount());
		assertEquals(YELLOW, gains.get(0).color());
	}

	@Test
	public void readsBothLinesOfATwoLineHologram() {
		// "+3ℯ\n+2𝕊" — the real shape from the mine.
		List<RaidMineGains.Gain> gains = RaidMineGains.parse(holo(
				"+", YELLOW, "3", YELLOW, "ℯ", YELLOW,
				"\n", 0xFFFFFF,
				"+", DARK_CYAN, "2", DARK_CYAN, "𝕊", DARK_CYAN));
		assertEquals(2, gains.size());
		assertEquals("ℯ", gains.get(0).code());
		assertEquals("𝕊", gains.get(1).code());
		assertEquals(2.0, gains.get(1).amount());
	}

	@Test
	public void keepsSurrogatePairCodesWhole() {
		// 𝕊, 𝕍 and 💰 all live outside the basic plane and are two java chars each;
		// reading a single char would capture half a surrogate pair and corrupt the code.
		for (String code : new String[] {"𝕊", "𝕍", "𝕡", "💰", "🅕"}) {
			List<RaidMineGains.Gain> gains = RaidMineGains.parse(
					holo("+", YELLOW, "5", YELLOW, code, YELLOW));
			assertEquals(1, gains.size(), "failed for " + code);
			assertEquals(code, gains.get(0).code());
		}
	}

	@Test
	public void readsFractionalAmounts() {
		List<RaidMineGains.Gain> gains = RaidMineGains.parse(
				holo("+", BLUE, "0.1", BLUE, "®", BLUE));
		assertEquals(0.1, gains.get(0).amount());
		assertEquals("Raid Points", RaidMineResources.name(gains.get(0).code()));
	}

	@Test
	public void colourSeparatesTheTiersSharingALetter() {
		// Score Flux and Score Essence differ only by case and colour.
		assertEquals("Score Flux", RaidMineResources.name("𝕤"));
		assertEquals("Score Essence", RaidMineResources.name("𝕊"));
	}

	@Test
	public void ignoresTextWithNoGains() {
		assertTrue(RaidMineGains.parse(holo("No Bounty", 0xAAAAAA)).isEmpty());
		assertTrue(RaidMineGains.parse(holo("132d:13h:09m", 0xBF0000)).isEmpty());
	}

	@Test
	public void unknownCodeFallsBackToItsGlyph() {
		List<RaidMineGains.Gain> gains = RaidMineGains.parse(
				holo("+", YELLOW, "7", YELLOW, "Ω", YELLOW));
		assertEquals("Ω", RaidMineResources.name(gains.get(0).code()));
		assertFalse(RaidMineResources.isKnown("Ω"));
	}
}
