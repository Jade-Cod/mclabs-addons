package dev.jade.labsaddons.crate;

import java.util.List;

/**
 * What the crate spin is doing, read off the container and nothing else.
 *
 * <p>The server's screen is three rows of nine. The middle row holds a candidate reward per
 * column; the rows above and below frame it in a pane coloured for that candidate's rarity.
 * A spin runs in five beats, and all ten captured crates run them identically:
 *
 * <ol>
 *   <li><b>fill</b> — nine columns land left to right, one every ~100ms.</li>
 *   <li><b>hold</b> — ~900ms of nothing, the longest single pause in the spin.</li>
 *   <li><b>cull</b> — eight columns revert to grey one at a time, and the gaps
 *       <em>lengthen</em>: the k-th gap is about {@code 100·k} ms, measured the same across
 *       all ten crates. Nothing here predicts from that ramp — the board draws each cull as
 *       it lands — but it is why the spin feels like it is slowing down rather than stalling.</li>
 *   <li><b>settle</b> — the survivor walks left to the centre column.</li>
 *   <li><b>land</b> — every pane turns the winner's colour.</li>
 * </ol>
 *
 * <p>Minecraft-free on purpose, so a whole captured spin can be replayed through it in a
 * test. The one thing it must not get wrong is beat four: the winner <em>changes column</em>
 * as it walks, and read naively that looks like one more cull and one more arrival. Once a
 * single candidate is left this stops tracking culls and only follows where it went.
 */
public final class CrateSpin {
	public static final int COLUMNS = 9;
	/** Where the winner ends up, and where the reward finally sits. */
	public static final int CENTRE_COLUMN = 4;
	/** Culls needed to get from nine candidates to one. */
	public static final int TOTAL_CULLS = COLUMNS - 1;

	public enum Phase {
		/** Candidates still arriving. */
		FILLING,
		/** All nine up, nothing culled yet. */
		HOLDING,
		/** Being narrowed down. */
		CULLING,
		/** One left, walking to the centre. */
		SETTLING,
		/** The whole screen has turned the winner's colour. */
		LANDED
	}

	/** One column of the container, as a frame shows it. */
	public record Cell(boolean hasItem, CrateRarity paneRarity, String name) {
		public static final Cell EMPTY = new Cell(false, null, "");
	}

	public enum ColumnState {
		/** Not filled yet. */
		UNSEEN,
		/** Holding a live candidate. */
		ALIVE,
		/** Was alive and has been eliminated. */
		CULLED
	}

	/** A column's state plus when it last changed, which is what the animation needs. */
	public static final class Column {
		private ColumnState state = ColumnState.UNSEEN;
		private CrateRarity rarity;
		private String name = "";
		private long changedAtMs;

		public ColumnState state() {
			return state;
		}

		public CrateRarity rarity() {
			return rarity;
		}

		public String name() {
			return name;
		}

		/** When this column last entered its current state, for easing in or out of it. */
		public long changedAtMs() {
			return changedAtMs;
		}

		public boolean alive() {
			return state == ColumnState.ALIVE;
		}
	}

	private final Column[] columns = new Column[COLUMNS];
	private Phase phase = Phase.FILLING;
	private int culls;
	private int winnerColumn = -1;
	private long landedAtMs;

	public CrateSpin() {
		for (int i = 0; i < COLUMNS; i++) {
			columns[i] = new Column();
		}
	}

	/**
	 * Takes one frame of the container.
	 *
	 * @param cells one per column, left to right
	 */
	public void observe(List<Cell> cells, long nowMs) {
		if (cells == null || cells.size() < COLUMNS) {
			return;
		}
		int aliveNow = 0;
		for (int i = 0; i < COLUMNS; i++) {
			if (cells.get(i).hasItem()) {
				aliveNow++;
			}
		}
		// Past the culling, a column emptying is the winner stepping out of it rather than a
		// candidate dying, so the two cases are kept strictly apart.
		if (settled()) {
			follow(cells, nowMs);
		} else {
			narrow(cells, aliveNow, nowMs);
		}
		retitle(cells);
		phase = phaseFor(cells, aliveNow, nowMs);
		if (phase == Phase.LANDED && landedAtMs == 0L) {
			landedAtMs = nowMs;
		}
	}

	/** Beats one to three: candidates arriving, then being eliminated. */
	private void narrow(List<Cell> cells, int aliveNow, long nowMs) {
		for (int i = 0; i < COLUMNS; i++) {
			Cell cell = cells.get(i);
			Column column = columns[i];
			if (cell.hasItem()) {
				if (column.state != ColumnState.ALIVE) {
					column.state = ColumnState.ALIVE;
					column.changedAtMs = nowMs;
				}
			} else if (column.state == ColumnState.ALIVE) {
				column.state = ColumnState.CULLED;
				column.changedAtMs = nowMs;
				culls++;
			}
		}
		if (culls >= TOTAL_CULLS) {
			winnerColumn = soleAlive(cells);
		}
	}

	/**
	 * Beat four: the survivor is walking left.
	 *
	 * <p>The column it steps out of goes back to {@link ColumnState#UNSEEN} rather than being
	 * marked culled, because nothing died there — the thing that was in it moved. Calling it a
	 * cull drew a second copy of the winner falling out of every column it passed through, since
	 * the vent animation keys off exactly that state and the column had only just changed.
	 */
	private void follow(List<Cell> cells, long nowMs) {
		int found = soleAlive(cells);
		if (found < 0 || found == winnerColumn) {
			return;
		}
		Column from = winnerColumn >= 0 ? columns[winnerColumn] : null;
		Column to = columns[found];
		if (from != null) {
			// Carry the identity across, because the new column was culled a moment ago and
			// its own remembered rarity is the candidate that died there.
			to.rarity = from.rarity;
			to.name = from.name;
			from.state = ColumnState.UNSEEN;
		}
		to.state = ColumnState.ALIVE;
		to.changedAtMs = nowMs;
		winnerColumn = found;
	}

	/** Learns each live column's rarity and name. The pane can lag its item by a frame. */
	private void retitle(List<Cell> cells) {
		for (int i = 0; i < COLUMNS; i++) {
			Cell cell = cells.get(i);
			Column column = columns[i];
			if (!cell.hasItem()) {
				continue;
			}
			if (cell.name() != null && !cell.name().isEmpty()) {
				column.name = cell.name();
			}
			if (cell.paneRarity() != null) {
				column.rarity = cell.paneRarity();
			}
		}
	}

	private Phase phaseFor(List<Cell> cells, int aliveNow, long nowMs) {
		if (culls < TOTAL_CULLS) {
			if (culls > 0) {
				return Phase.CULLING;
			}
			return aliveNow >= COLUMNS ? Phase.HOLDING : Phase.FILLING;
		}
		return flashed(cells) ? Phase.LANDED : Phase.SETTLING;
	}

	/**
	 * The final frame turns every pane the winner's colour. Before it, only the winner's own
	 * column is coloured and the other eight are grey, so this cannot fire early.
	 */
	private boolean flashed(List<Cell> cells) {
		CrateRarity won = winner() == null ? null : winner().rarity();
		if (won == null) {
			return false;
		}
		for (int i = 0; i < COLUMNS; i++) {
			if (cells.get(i).paneRarity() != won) {
				return false;
			}
		}
		return true;
	}

	private static int soleAlive(List<Cell> cells) {
		int found = -1;
		for (int i = 0; i < COLUMNS; i++) {
			if (!cells.get(i).hasItem()) {
				continue;
			}
			if (found >= 0) {
				return -1;
			}
			found = i;
		}
		return found;
	}

	private boolean settled() {
		return culls >= TOTAL_CULLS;
	}

	// --- what the board draws from -------------------------------------------

	public Phase phase() {
		return phase;
	}

	public Column column(int index) {
		return columns[index];
	}

	/**
	 * Visible for testing: how many candidates have been eliminated. Nothing drawn uses it,
	 * but it is how a test sees that the winner's walk to the centre added none.
	 */
	int culls() {
		return culls;
	}

	public int aliveCount() {
		int alive = 0;
		for (Column column : columns) {
			if (column.alive()) {
				alive++;
			}
		}
		return alive;
	}

	/** The single surviving column once there is one, or null while the field is still wide. */
	public Column winner() {
		return winnerColumn >= 0 ? columns[winnerColumn] : null;
	}

	public int winnerColumn() {
		return winnerColumn;
	}

	public long landedAtMs() {
		return landedAtMs;
	}

	/**
	 * Visible for testing: the best rarity still in play.
	 *
	 * <p>Nothing drawn asks for it any more — every candidate carries its own rarity's glow, so
	 * the top of the field is something you see rather than something the board computes. It
	 * stays because it is how a test watches that top fall at the right cull, which is the
	 * behaviour the whole board is built on.
	 */
	CrateRarity bestAlive() {
		CrateRarity best = null;
		for (Column column : columns) {
			if (column.alive() && column.rarity() != null
					&& (best == null || column.rarity().compareTo(best) > 0)) {
				best = column.rarity();
			}
		}
		return best;
	}
}
