package dev.jade.labsaddons.casino;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerHoldTest {
	private static final long STALE_MS = 1_000L;
	private static final long REOPEN_MS = 400L;
	private static final String FLIP = "Flipping a coin...";

	private static List<SlotView> empty() {
		List<SlotView> slots = new ArrayList<>();
		for (int i = 0; i < 45; i++) {
			slots.add(SlotView.empty(i));
		}
		return slots;
	}

	private static List<SlotView> filled(String coin) {
		List<SlotView> slots = empty();
		slots.set(22, new SlotView(22, coin, List.of(), 1));
		return slots;
	}

	@Test
	void theFirstFrameOfAMenuIsANewContainer() {
		ContainerHold hold = new ContainerHold();
		ContainerHold.Result first = hold.offer(filled("GonjaMayor"), true, 4, FLIP, 0L,
				STALE_MS, REOPEN_MS);
		assertTrue(first.containerChanged());
		assertTrue(hold.pinnedTo(4));
		assertFalse(hold.pinnedTo(5));
	}

	@Test
	void aFrameOfTheSameContainerIsNotANewOne() {
		ContainerHold hold = new ContainerHold();
		hold.offer(filled("GonjaMayor"), true, 4, FLIP, 0L, STALE_MS, REOPEN_MS);
		assertFalse(hold.offer(filled("Ophiliah"), true, 4, FLIP, 150L, STALE_MS, REOPEN_MS)
				.containerChanged());
	}

	@Test
	void anEmptyReopenIsRiddenOutRatherThanDropped() {
		// The flash: these menus close and reopen on a click and are empty for a tick or
		// two. Dropping the board there lets the real chest through.
		ContainerHold hold = new ContainerHold();
		List<SlotView> shown = filled("GonjaMayor");
		hold.offer(shown, true, 4, FLIP, 0L, STALE_MS, REOPEN_MS);

		ContainerHold.Result gap = hold.offer(empty(), false, 5, FLIP, 60L, STALE_MS,
				REOPEN_MS);
		assertSame(shown, gap.slots(), "it should keep drawing the last good frame");
		assertFalse(gap.containerChanged());
		assertTrue(hold.pinnedTo(5), "but answer clicks for the menu really in front of us");
	}

	@Test
	void aSecondGameIsANewContainerEvenAfterRidingOutItsReopen() {
		// The bug this class was extracted for. Two coinflips in a row share a title, so
		// the reopen between them is ridden out — and the board used to pin itself to the
		// new id while holding, which left nothing for the next real frame to notice. The
		// second flip opened still showing the first one's result.
		ContainerHold hold = new ContainerHold();
		hold.offer(filled("GonjaMayor"), true, 4, FLIP, 0L, STALE_MS, REOPEN_MS);
		hold.offer(filled("You won!"), true, 4, FLIP, 6_000L, STALE_MS, REOPEN_MS);

		hold.offer(empty(), false, 5, FLIP, 20_000L, STALE_MS, REOPEN_MS);
		ContainerHold.Result second = hold.offer(filled("xFunnyBone27"), true, 5, FLIP,
				20_060L, STALE_MS, REOPEN_MS);
		assertTrue(second.containerChanged(),
				"the second game must reset the board, not inherit the first one's result");
	}

	@Test
	void aDifferentMenuIsHandedBackAtOnce() {
		ContainerHold hold = new ContainerHold();
		hold.offer(filled("GonjaMayor"), true, 4, FLIP, 0L, STALE_MS, REOPEN_MS);
		// A new id whose contents are not ours and whose title disagrees.
		ContainerHold.Result other = hold.offer(filled("Mines: 9"), false, 5,
				"Active Coinflips", 60L, STALE_MS, REOPEN_MS);
		assertNull(other.slots());
	}

	@Test
	void aReopenThatNeverFillsIsGivenUpOn() {
		ContainerHold hold = new ContainerHold();
		hold.offer(filled("GonjaMayor"), true, 4, FLIP, 0L, STALE_MS, REOPEN_MS);
		// The clock starts at the first frame that will not parse, not at the last one
		// that did.
		hold.offer(empty(), false, 5, FLIP, 100L, STALE_MS, REOPEN_MS);
		assertNull(hold.offer(empty(), false, 5, FLIP, 100L + REOPEN_MS + 1L, STALE_MS,
				REOPEN_MS).slots(), "a reopen that never fills is not a reopen");
	}

	@Test
	void aMenuThatStopsParsingInPlaceIsHeldLongerThanAReopen() {
		ContainerHold hold = new ContainerHold();
		List<SlotView> shown = filled("GonjaMayor");
		hold.offer(shown, true, 4, FLIP, 0L, STALE_MS, REOPEN_MS);
		hold.offer(empty(), false, 4, FLIP, 100L, STALE_MS, REOPEN_MS);
		// Same container id, so it gets the longer grace: past a reopen's patience, still
		// holding.
		assertSame(shown, hold.offer(empty(), false, 4, FLIP, 100L + REOPEN_MS + 1L, STALE_MS,
				REOPEN_MS).slots());
		assertNull(hold.offer(empty(), false, 4, FLIP, 100L + STALE_MS + 1L, STALE_MS,
				REOPEN_MS).slots());
	}

	@Test
	void releasingForgetsTheContainerEntirely() {
		ContainerHold hold = new ContainerHold();
		hold.offer(filled("GonjaMayor"), true, 4, FLIP, 0L, STALE_MS, REOPEN_MS);
		hold.release();
		assertFalse(hold.pinnedTo(4));
		assertFalse(hold.holding());
		assertTrue(hold.offer(filled("GonjaMayor"), true, 4, FLIP, 100L, STALE_MS, REOPEN_MS)
				.containerChanged(), "the same id after a release is still a fresh start");
	}
}
