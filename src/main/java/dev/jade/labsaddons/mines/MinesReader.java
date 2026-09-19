package dev.jade.labsaddons.mines;

import dev.jade.labsaddons.casino.CasinoPanel;
import dev.jade.labsaddons.casino.Money;
import dev.jade.labsaddons.casino.SlotView;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the in-play Mines container into a {@link MinesState}.
 *
 * <p>Every name and pattern below is what the server actually sent in
 * {@code mines.jsonl}. Like the Double² reader this runs every frame, because the lore
 * changing is the entire point.
 */
public final class MinesReader {
	/** The 5x5 grid, in reading order. Rows of five starting at 2, 9 apart. */
	public static final int[] TILE_SLOTS = tileSlots();
	/** The tnt that states the mine count. */
	public static final int MINE_COUNT_SLOT = 18;
	/** The emerald that states the stake. */
	public static final int STAKE_SLOT = 26;
	/** The diamond that offers the cash out; a plain pane when there is nothing to take. */
	public static final int CASH_OUT_SLOT = 49;

	private static final String HIDDEN_NAME = "???";
	private static final String SAFE_NAME = "safe";
	private static final String MINE_NAME = "mine";

	private static final Pattern MINE_COUNT = Pattern.compile("^\\s*Mines:\\s*(\\d+)\\s*$",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern STAKE = Pattern.compile("^\\s*Investment:\\s*\\$[\\d,.]+\\s*$",
			Pattern.CASE_INSENSITIVE);
	/** "for $3,828.25" — what cashing out now pays. */
	private static final Pattern CASH_NOW = Pattern.compile("^\\s*for\\s+(\\$[\\d,]+(?:\\.\\d{1,2})?)",
			Pattern.CASE_INSENSITIVE);
	/** "star for $6,125" — what one more star would pay. */
	private static final Pattern CASH_NEXT = Pattern.compile(
			"star\\s+for\\s+(\\$[\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);

	private MinesReader() {
	}

	private static int[] tileSlots() {
		int[] slots = new int[MinesOdds.TILES];
		int at = 0;
		for (int row = 0; row < 5; row++) {
			for (int column = 0; column < 5; column++) {
				slots[at++] = 2 + row * 9 + column;
			}
		}
		return slots;
	}

	/**
	 * The cheap probe: the two figures the menu always states. Two slot names, because
	 * this runs against every container screen in the game twice a frame.
	 */
	public static boolean looksLikeMines(String mineCountName, String stakeName) {
		return MINE_COUNT.matcher(mineCountName).matches()
				&& STAKE.matcher(stakeName).matches();
	}

	/**
	 * Whether these slots are the in-play Mines menu.
	 *
	 * <p>Recognised by its contents, not its title, so it survives a rename. At least one
	 * tile has to be readable as well as the two figures — the grid is what the board is
	 * for, and a container with neither is something else entirely.
	 */
	public static boolean isMines(List<SlotView> slots) {
		if (slots == null || slots.size() < CasinoPanel.CONTAINER_SLOTS) {
			return false;
		}
		if (!looksLikeMines(SlotView.nameAt(slots, MINE_COUNT_SLOT),
				SlotView.nameAt(slots, STAKE_SLOT))) {
			return false;
		}
		for (int slot : TILE_SLOTS) {
			if (tile(SlotView.nameAt(slots, slot)) != null) {
				return true;
			}
		}
		return false;
	}

	public static MinesState read(List<SlotView> slots) {
		List<MinesState.Tile> tiles = new ArrayList<>(MinesOdds.TILES);
		for (int slot : TILE_SLOTS) {
			MinesState.Tile tile = tile(SlotView.nameAt(slots, slot));
			// A slot that reads as nothing is face down as far as the board is concerned:
			// mid-update and mid-prediction both look like this, and neither is a reveal.
			tiles.add(tile == null ? MinesState.Tile.HIDDEN : tile);
		}
		SlotView cashOut = SlotView.at(slots, CASH_OUT_SLOT);
		return new MinesState(
				Money.parseCents(SlotView.nameAt(slots, STAKE_SLOT)),
				mineCount(SlotView.nameAt(slots, MINE_COUNT_SLOT)),
				List.copyOf(tiles),
				cashCents(cashOut, CASH_NOW),
				cashCents(cashOut, CASH_NEXT));
	}

	/** Which tile slot a grid cell sits in, so a click can be forwarded to it. */
	public static int tileSlot(int row, int column) {
		return TILE_SLOTS[row * 5 + column];
	}

	static MinesState.Tile tile(String name) {
		if (name == null || name.isEmpty()) {
			return null;
		}
		String trimmed = name.trim();
		if (trimmed.equals(HIDDEN_NAME)) {
			return MinesState.Tile.HIDDEN;
		}
		if (trimmed.equalsIgnoreCase(SAFE_NAME)) {
			return MinesState.Tile.SAFE;
		}
		if (trimmed.equalsIgnoreCase(MINE_NAME)) {
			return MinesState.Tile.MINE;
		}
		return null;
	}

	static int mineCount(String name) {
		Matcher matcher = MINE_COUNT.matcher(name == null ? "" : name);
		if (!matcher.matches()) {
			return 0;
		}
		try {
			return Integer.parseInt(matcher.group(1));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/**
	 * The first figure in the Cash Out lore matching {@code pattern}, in cents, or 0.
	 *
	 * <p>Both lines are checked against the whole lore rather than a fixed index: the
	 * server leaves blank lines between them and the layout is not ours to depend on.
	 */
	private static long cashCents(SlotView slot, Pattern pattern) {
		if (slot == null || slot.lore() == null) {
			return 0L;
		}
		for (String line : slot.lore()) {
			Matcher matcher = pattern.matcher(line == null ? "" : line);
			if (matcher.find()) {
				return Money.parseCents(matcher.group(1));
			}
		}
		return 0L;
	}
}
