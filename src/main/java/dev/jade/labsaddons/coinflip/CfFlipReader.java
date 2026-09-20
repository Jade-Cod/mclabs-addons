package dev.jade.labsaddons.coinflip;

import dev.jade.labsaddons.casino.SlotView;

import java.util.List;
import java.util.Locale;

/**
 * Reads the "Flipping a coin..." menu, which is one head and forty-four panes.
 *
 * <p>The server animates the flip by swapping slot 22's head between the two players,
 * nineteen times over six seconds, slowing as it goes. Two things about that matter:
 *
 * <ul>
 *   <li>Only one name is up at a time, so both players are learned over the first swap
 *       rather than read off the screen at once.</li>
 *   <li><b>The last face shown is not the winner.</b> Verified: mine was up when I lost.
 *       Only {@link Result} says who won, and it arrives with the result frame.</li>
 * </ul>
 *
 * <p>The result frame turns all forty-five panes red on a loss and green on a win, but only
 * slot 22 is read: the verdict is in its name either way, and a colour is not something a
 * slot view carries.
 *
 * <p>Nothing here is clickable — the chest is a screen, not a menu.
 */
public final class CfFlipReader {
	public static final int COIN_SLOT = 22;
	/** Panes either side of the coin, used to tell this menu's shape from a chest's. */
	public static final int LEFT_PANE_SLOT = 21;
	public static final int RIGHT_PANE_SLOT = 23;
	/** What the title says while a coin is in the air. */
	public static final String TITLE = "flipping a coin";

	public enum Result {
		RUNNING,
		WON,
		LOST
	}

	/**
	 * @param facing the player whose head is up, or "" once the flip has resolved
	 */
	public record Flip(String facing, Result result) {
		public boolean running() {
			return result == Result.RUNNING;
		}
	}

	private CfFlipReader() {
	}

	/**
	 * The cheap probe, run against every container menu in the game: one named head with
	 * blank panes either side. Deliberately loose — the title is what actually decides, in
	 * {@link #isFlip}, because this shape is far too ordinary to latch onto on its own.
	 */
	public static boolean looksLikeFlip(boolean coinIsHead, boolean leftIsBlankPane,
			boolean rightIsBlankPane) {
		return coinIsHead && leftIsBlankPane && rightIsBlankPane;
	}

	public static boolean isFlip(List<SlotView> slots, String title) {
		return title != null
				&& title.toLowerCase(Locale.ROOT).contains(TITLE)
				&& !SlotView.nameAt(slots, COIN_SLOT).isEmpty();
	}

	public static Flip read(List<SlotView> slots) {
		String name = SlotView.nameAt(slots, COIN_SLOT).trim();
		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.contains("you won") || lower.contains("you win")) {
			return new Flip("", Result.WON);
		}
		if (lower.contains("you lost") || lower.contains("you lose")) {
			return new Flip("", Result.LOST);
		}
		return new Flip(name, Result.RUNNING);
	}
}
