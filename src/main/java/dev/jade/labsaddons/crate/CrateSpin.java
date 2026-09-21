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
 *       <em>lengthen</em>: the k-th gap is about {@code 100·k} ms. That ramp is the reason
 *       {@link #nextCullAtMs} can be honest rather than a guess.</li>
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

	/**
	 * The k-th cull gap, in ms: the ramp measured across all ten captured spins, where the
	 * gaps came out at roughly 100, 200, 300 … 700. Used only to draw how long is left, so
	 * being a little out costs a bar that finishes early, not a wrong reading.
	 */
	private static final long CULL_STEP_MS = 100L;
	/** The pause between the last column landing and the first cull. Measured 851–947ms. */
	private static final long HOLD_MS = 900L;

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
	private long lastCullAtMs;
	private long filledAtMs;
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
				lastCullAtMs = nowMs;
			}
		}
		if (aliveNow >= COLUMNS && filledAtMs == 0L) {
			filledAtMs = nowMs;
		}
		if (culls >= TOTAL_CULLS) {
			winnerColumn = soleAlive(cells);
		}
	}

	/**
	 * Beat four: the survivor is walking left. Its old column is not a casualty, so it is
	 * left showing what it held rather than being marked culled.
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
			from.state = ColumnState.CULLED;
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

	public int culls() {
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

	/** The best rarity still in play — the one thing the vanilla screen makes you squint for. */
	public CrateRarity bestAlive() {
		CrateRarity best = null;
		for (Column column : columns) {
			if (column.alive() && column.rarity() != null
					&& (best == null || column.rarity().compareTo(best) > 0)) {
				best = column.rarity();
			}
		}
		return best;
	}

	/** Whether a rarity has any column left holding it. */
	public boolean aliveAt(CrateRarity rarity) {
		for (Column column : columns) {
			if (column.alive() && column.rarity() == rarity) {
				return true;
			}
		}
		return false;
	}

	/**
	 * When the next cull is due, from the server's own ramp, or 0 when none is expected.
	 * Predicted rather than waited for so the tension can be drawn while it builds; a cull
	 * that lands early or late simply resets the bar.
	 */
	public long nextCullAtMs() {
		if (culls >= TOTAL_CULLS) {
			return 0L;
		}
		if (culls == 0) {
			return filledAtMs == 0L ? 0L : filledAtMs + HOLD_MS;
		}
		return lastCullAtMs + CULL_STEP_MS * (culls + 1L);
	}

	/** How far along the wait for the next cull is, 0 to 1, or 0 when nothing is pending. */
	public float tension(long nowMs) {
		long due = nextCullAtMs();
		if (due <= 0L) {
			return 0f;
		}
		long from = culls == 0 ? filledAtMs : lastCullAtMs;
		long span = due - from;
		if (span <= 0L) {
			return 0f;
		}
		return Math.clamp((nowMs - from) / (float) span, 0f, 1f);
	}
}
