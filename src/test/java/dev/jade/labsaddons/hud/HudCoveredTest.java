package dev.jade.labsaddons.hud;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The rule deciding whether the widgets get out from under something drawn on top.
 *
 * <p>Worth pinning because the vanilla HUD renders whether or not a screen is open, so a
 * regression here is not a crash — it is widgets quietly drawing behind your inventory
 * again, which looks like nothing at all until you notice the clutter.
 */
class HudCoveredTest {
	@Test
	void nothingOnTopDrawsTheWidgets() {
		assertFalse(HudRenderDispatcher.covers(false, false, false));
	}

	@Test
	void anOrdinaryScreenHidesThem() {
		// Inventory, escape menu, a casino board — anything that is not chat.
		assertTrue(HudRenderDispatcher.covers(false, true, false));
	}

	@Test
	void chatLeavesThemUp() {
		// Chat is open for as long as you are typing and the game stays visible behind it.
		assertFalse(HudRenderDispatcher.covers(false, true, true));
	}

	@Test
	void theDebugOverlayHidesThem() {
		// F3 is not a Screen, so it is asked about on its own.
		assertTrue(HudRenderDispatcher.covers(true, false, false));
	}

	@Test
	void debugOverlayWinsOverChat() {
		// Both can be up at once: F3 stays on screen while you type.
		assertTrue(HudRenderDispatcher.covers(true, true, true));
	}
}
