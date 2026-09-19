package dev.jade.labsaddons.mines;

import dev.jade.labsaddons.casino.Money;

import java.util.List;

/**
 * One frame's reading of the Mines board. Everything here came off the container the
 * server sent; nothing is remembered, so a stale figure cannot survive into the next game.
 *
 * @param tiles         the 5x5 grid in reading order, 25 entries
 * @param cashOutCents  what the Cash Out item offers right now, or 0 when it offers
 *                      nothing — before the first star, and once the game is over
 * @param nextCents     what it says one more star would be worth, or 0
 */
public record MinesState(long stakeCents, int mines, List<Tile> tiles,
		long cashOutCents, long nextCents) {

	public enum Tile {
		/** Face down. */
		HIDDEN,
		/** A revealed star. */
		SAFE,
		/** A revealed mine. */
		MINE
	}

	public int stars() {
		return count(Tile.SAFE);
	}

	/** True once a mine is showing: the game is lost and the grid is being unveiled. */
	public boolean blown() {
		return count(Tile.MINE) > 0;
	}

	/** Whether the server is offering a cash out — the only time that button is live. */
	public boolean canCashOut() {
		return cashOutCents > 0 && !blown();
	}

	private int count(Tile wanted) {
		int found = 0;
		for (Tile tile : tiles) {
			if (tile == wanted) {
				found++;
			}
		}
		return found;
	}

	/**
	 * The figure to show for {@code stars} safe tiles: the server's own where it has
	 * stated one, otherwise derived from {@link MinesOdds}.
	 */
	public long rungCents(int stars) {
		int here = stars();
		if (stars == here && cashOutCents > 0) {
			return cashOutCents;
		}
		if (stars == here + 1 && nextCents > 0) {
			return nextCents;
		}
		return MinesOdds.payoutCents(stakeCents, mines, stars);
	}

	/** Whether {@link #rungCents} had to work that rung out for itself. */
	public boolean rungIsDerived(int stars) {
		int here = stars();
		return !(stars == here && cashOutCents > 0)
				&& !(stars == here + 1 && nextCents > 0);
	}

	/** True when cashing out at this rung returns less than was staked. */
	public boolean rungUnderWater(int stars) {
		return rungCents(stars) < stakeCents;
	}

	public String stakeText() {
		return Money.format(stakeCents);
	}
}
