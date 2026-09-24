package dev.jade.labsaddons.casino;

import java.util.List;

/**
 * The bookkeeping behind {@link CasinoPanel}'s hold: which container a board is answering
 * for, which one its slots actually came from, and whether a frame that will not parse is
 * a re-send to ride out or a different menu to hand back.
 *
 * <p>Its own class because the sequence is subtle enough to have shipped wrong twice, and
 * because as a plain object it can be driven through a whole game in a test without a
 * screen. Both bugs came from the same place:
 *
 * <ul>
 *   <li>These menus close and reopen on a click, and are empty for a tick or two. Dropping
 *       the board on those frames lets the real chest through, which reads as a flash.</li>
 *   <li>But riding one out means answering for the <em>new</em> container while still
 *       drawing the <em>old</em> one's slots. Conflating those two ids made a second
 *       coinflip open still holding the first one's result: the board pinned itself to the
 *       new id while holding, so when the new container finally filled, nothing had
 *       changed as far as it could tell, and it never reset.</li>
 * </ul>
 */
final class ContainerHold {
	/**
	 * What a frame resolved to.
	 *
	 * @param slots            what to draw, or null when this is not our menu
	 * @param containerChanged true when these slots belong to a container we have not drawn
	 *                         before, so the board must drop anything it remembers
	 */
	record Result(List<SlotView> slots, boolean containerChanged) {
		static final Result NOTHING = new Result(null, false);
	}

	/** The container we answer clicks for. */
	private int pinnedSyncId = -1;
	/** The container the held slots actually came from. Never the same thing. */
	private int contentSyncId = -1;
	private List<SlotView> heldSlots;
	private String heldKey = "";
	private long staleSinceMs;

	/**
	 * @param parses   whether these slots are this board's menu
	 * @param staleMs  how long to hold a container that has stopped parsing in place
	 * @param reopenMs how long to hold across a new container id, which is shorter because
	 *                 holding one menu's board over a different menu is the failure that
	 *                 costs
	 */
	Result offer(List<SlotView> slots, boolean parses, int syncId, String key, long nowMs,
			long staleMs, long reopenMs) {
		if (parses) {
			boolean changed = contentSyncId != syncId;
			heldSlots = slots;
			heldKey = key;
			pinnedSyncId = syncId;
			contentSyncId = syncId;
			staleSinceMs = 0L;
			return new Result(slots, changed);
		}
		if (heldSlots == null) {
			return Result.NOTHING;
		}
		// Measured against the container the held slots came from, not the one we have
		// pinned to. Pinning happens on the gap's first frame, so measuring against that
		// would make every frame after it look like the same container quietly stopping —
		// and hand a reopen the longer grace meant for exactly the opposite case.
		boolean reopened = contentSyncId != syncId;
		// A new container id is not by itself a different menu, so this holds while the new
		// one is both empty and still calling itself what it did before — and hands over at
		// once when it is neither.
		if (reopened && !(isUnfilled(slots) && heldKey.equals(key))) {
			return Result.NOTHING;
		}
		if (staleSinceMs == 0L) {
			staleSinceMs = nowMs;
		}
		if (nowMs - staleSinceMs > (reopened ? reopenMs : staleMs)) {
			// Not a re-send — something else is behind this now.
			return Result.NOTHING;
		}
		// Answer for the screen actually in front of us, so a click still lands. The
		// content id deliberately stays where it was: these are not that container's slots.
		pinnedSyncId = syncId;
		return new Result(heldSlots, false);
	}

	/** True once this board has taken a container over and not yet given it back. */
	boolean pinnedTo(int syncId) {
		return pinnedSyncId >= 0 && pinnedSyncId == syncId;
	}

	boolean holding() {
		return heldSlots != null;
	}

	void release() {
		pinnedSyncId = -1;
		contentSyncId = -1;
		heldSlots = null;
		heldKey = "";
		staleSinceMs = 0L;
	}

	/** Whether the server has put anything in this container yet. */
	private static boolean isUnfilled(List<SlotView> slots) {
		for (SlotView slot : slots) {
			if (!slot.isEmpty()) {
				return false;
			}
		}
		return true;
	}
}
