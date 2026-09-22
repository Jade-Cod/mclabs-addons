package dev.jade.labsaddons.coinflip;

import dev.jade.labsaddons.casino.SlotView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CfFlipReaderTest {
	private static final int SLOTS = 45;
	private static final String TITLE = "Flipping a coin...";

	private static List<SlotView> flip(String coinName) {
		List<SlotView> slots = new ArrayList<>();
		for (int i = 0; i < SLOTS; i++) {
			// Every pane the server sends is blank-named, which is what an empty SlotView is.
			slots.add(SlotView.empty(i));
		}
		slots.set(CfFlipReader.COIN_SLOT,
				new SlotView(CfFlipReader.COIN_SLOT, coinName, List.of(), 1));
		return slots;
	}

	@Test
	void theTitleIsWhatIdentifiesThisMenu() {
		assertTrue(CfFlipReader.isFlip(flip("GonjaMayor"), TITLE));
		// Forty-four panes and one head is far too ordinary a shape to latch onto alone.
		assertFalse(CfFlipReader.isFlip(flip("GonjaMayor"), "Active Coinflips"));
		assertFalse(CfFlipReader.isFlip(flip(""), TITLE));
	}

	@Test
	void theHeadUpIsWhoeverTheServerLastNamed() {
		assertEquals("GonjaMayor", CfFlipReader.read(flip("GonjaMayor")).facing());
		assertEquals("Ophiliah", CfFlipReader.read(flip("Ophiliah")).facing());
		assertTrue(CfFlipReader.read(flip("GonjaMayor")).running());
	}

	@Test
	void theResultFrameReplacesTheHeadWithTheVerdict() {
		// Captured: slot 22 becomes "You lost!" and all forty-five panes turn red. A win is
		// the same frame in green. Only slot 22 is read, so the glass colour never matters.
		assertEquals(CfFlipReader.Result.LOST, CfFlipReader.read(flip("You lost!")).result());
		assertEquals(CfFlipReader.Result.WON, CfFlipReader.read(flip("You won!")).result());
		assertFalse(CfFlipReader.read(flip("You lost!")).running());
		assertEquals("", CfFlipReader.read(flip("You won!")).facing());
	}

	@Test
	void theCheapProbeWantsAHeadBetweenTwoBlankPanes() {
		assertTrue(CfFlipReader.looksLikeFlip(true, true, true));
		assertFalse(CfFlipReader.looksLikeFlip(false, true, true));
		assertFalse(CfFlipReader.looksLikeFlip(true, false, true));
	}
}
