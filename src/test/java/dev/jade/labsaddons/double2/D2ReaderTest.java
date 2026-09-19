package dev.jade.labsaddons.double2;

import dev.jade.labsaddons.double2.D2Reader.SlotView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Built from the two rounds captured on 2026-09-18 — every name and lore line below is
 * what the server actually sent, so a server-side wording change fails here rather than
 * silently leaving the board blank.
 */
class D2ReaderTest {
	private static final String[] STRIP_A =
			"RDL CQL MSL CQL ADL CQL ADL MSL CQL".split(" ");

	/** A container of 54 empty slots, ready to have the real ones filled in. */
	private static List<SlotView> blank() {
		List<SlotView> slots = new ArrayList<>();
		for (int i = 0; i < D2Reader.CONTAINER_SLOTS; i++) {
			slots.add(new SlotView(i, "", List.of(), 0));
		}
		return slots;
	}

	private static void set(List<SlotView> slots, int index, String name, String... lore) {
		slots.set(index, new SlotView(index, name, List.of(lore), 1));
	}

	/** Slots 2–6 and the wheel window: present in every phase. */
	private static List<SlotView> frame() {
		List<SlotView> slots = blank();
		set(slots, 2, "■ Crimson Quadri Lab (2.0x Profit)", "Select CQL.");
		set(slots, 3, "⭑ Magenta Star Lab (4.0x Profit)", "Select MSL.");
		set(slots, 4, "▲ Amber Delta Lab (6.0x Profit)", "Select ADL.");
		set(slots, 5, "◆ Royal Diamond Lab (11.0x Profit)", "Select RDL.");
		set(slots, 6, "● Emerald Orb Lab (24.0x Profit)", "Select EOL.");
		for (int i = 0; i < STRIP_A.length; i++) {
			Lab lab = Lab.valueOf(STRIP_A[i]);
			set(slots, 45 + i, lab.glyph() + " " + lab.shortName() + " Lab", lab.multiplierText() + " Profit");
		}
		return slots;
	}

	private static List<SlotView> betting() {
		List<SlotView> slots = frame();
		set(slots, 6, "● Emerald Orb Lab (24.0x Profit)", "Investing in EOL.");
		for (int i = D2Reader.CHIP_FIRST; i <= D2Reader.CHIP_LAST; i++) {
			set(slots, i, "+$100", "Current Investment: $100,000");
		}
		set(slots, 28, "1 Player Investing", "CQL: $0", "MSL: $0", "ADL: $0", "RDL: $0",
				"EOL: $100,000", "Total: $100,000");
		set(slots, 31, "Your Investment", "$100,000 in EOL");
		slots.set(34, new SlotView(34, "Last EOL Profit:", List.of("27 rounds ago."), 27));
		set(slots, 40, "Investing Open!", "Market closing in 34 seconds");
		return slots;
	}

	@Test
	void recognisesTheMenuByItsContents() {
		assertTrue(D2Reader.isDouble2(betting()));
		assertFalse(D2Reader.isDouble2(blank()));
		assertFalse(D2Reader.isDouble2(List.of()));
	}

	/**
	 * The server fills the menu a slot at a time, so the labs can arrive a frame or two
	 * before the wheel row. The board must stay down until both are there, or it draws
	 * with nothing in its middle.
	 */
	@Test
	void waitsForTheWheelRowBeforeClaimingTheMenu() {
		List<SlotView> half = betting();
		half.set(50, new SlotView(50, "", List.of(), 0));
		assertFalse(D2Reader.isDouble2(half));
	}

	@Test
	void readsTheBettingPhase() {
		D2State state = D2Reader.read(betting());
		assertEquals(D2State.Phase.BETTING, state.phase());
		assertEquals(34, state.secondsLeft());
		assertEquals(Lab.EOL, state.selected());
		assertEquals(100_000L, state.stake());
		assertEquals(Lab.EOL, state.betLab());
		assertEquals(100_000L, state.betAmount());
		assertTrue(state.hasBet());
		assertEquals(1, state.investors());
		assertEquals(27, state.eolDrought());
		assertEquals(100_000L, state.potTotal());
		assertEquals(0L, state.pot().get(Lab.CQL));
		assertEquals(100_000L, state.pot().get(Lab.EOL));
		assertEquals(Lab.ADL, state.pointerLab(), "slot 49 names the pointer directly");
		assertNull(state.settledLab());
	}

	@Test
	void readsTheSpinPhase() {
		List<SlotView> slots = betting();
		for (int i = 27; i <= 35; i++) {
			set(slots, i, "Simulating market...", "1 Player Investing", "CQL: $0", "MSL: $0",
					"ADL: $0", "RDL: $0", "EOL: $100,000", "Total: $100,000");
		}
		set(slots, 40, "Simulating market...");
		D2State state = D2Reader.read(slots);
		assertEquals(D2State.Phase.SPINNING, state.phase());
		assertEquals(100_000L, state.potTotal());
		assertEquals(1, state.investors());
		// Slot 31 is swallowed by the banner, so the locked bet is no longer readable there.
		assertNull(state.betLab());
	}

	/**
	 * Settled is detected off the banner, not the clock: for the two seconds the result is
	 * up, slot 40 still reads "Simulating market..." from the spin.
	 */
	@Test
	void readsTheSettledPhaseWhileTheClockStillSaysSimulating() {
		List<SlotView> slots = betting();
		for (int i = 27; i <= 35; i++) {
			set(slots, i, "ADL", "Nobody profited.");
		}
		set(slots, 40, "Simulating market...");
		D2State state = D2Reader.read(slots);
		assertEquals(D2State.Phase.SETTLED, state.phase());
		assertEquals(Lab.ADL, state.settledLab());
		assertEquals(Lab.ADL, state.pointerLab(), "the banner and slot 49 must agree");
	}

	@Test
	void survivesAnUnreadableWheel() {
		List<SlotView> slots = betting();
		set(slots, 49, "Mystery Lab", "0.0x Profit");
		D2State state = D2Reader.read(slots);
		assertNull(state.window().get(D2Ring.POINTER),
				"an unreadable pane comes through as a hole in the strip");
		// Everything else still reads, so the board degrades rather than disappearing.
		assertEquals(100_000L, state.stake());
	}

	@Test
	void readsAFreshRoundWithNothingStaked() {
		List<SlotView> slots = frame();
		for (int i = D2Reader.CHIP_FIRST; i <= D2Reader.CHIP_LAST; i++) {
			set(slots, i, "+$100", "Current Investment: $0");
		}
		for (int i = 27; i <= 35; i++) {
			set(slots, i, "Invest!");
		}
		slots.set(34, new SlotView(34, "Last EOL Profit:", List.of("28 rounds ago."), 28));
		set(slots, 40, "Investing Open!", "Market closing in 60 seconds");
		D2State state = D2Reader.read(slots);
		assertEquals(D2State.Phase.BETTING, state.phase());
		assertEquals(60, state.secondsLeft());
		assertEquals(0L, state.stake());
		assertFalse(state.hasBet());
		assertNull(state.selected());
		assertEquals(0, state.investors());
		assertEquals(0L, state.potTotal());
		assertEquals(28, state.eolDrought());
	}
}
