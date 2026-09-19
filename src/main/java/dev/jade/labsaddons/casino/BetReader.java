package dev.jade.labsaddons.casino;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns either game's betting screen into a {@link BetState}.
 *
 * <p>Mines and BondJoules share this menu almost exactly: the same seven-slot stake row,
 * the same emerald to start. Mines adds a pair of tnt to step the mine count; BondJoules
 * advertises shift-click for the minimum and maximum stake, which Mines does not. One
 * reader covers both, and which game it is falls out of what is there.
 */
public final class BetReader {
	public static final int MINUS_10K_SLOT = 19;
	public static final int MINUS_1K_SLOT = 20;
	public static final int MINUS_100_SLOT = 21;
	public static final int START_SLOT = 22;
	public static final int PLUS_100_SLOT = 23;
	public static final int PLUS_1K_SLOT = 24;
	public static final int PLUS_10K_SLOT = 25;
	/** Mines only: the tnt that adds a mine. */
	public static final int MORE_MINES_SLOT = 13;
	/** Mines only: the tnt that removes one. Its lore repeats the "increase" wording. */
	public static final int FEWER_MINES_SLOT = 31;
	/** BondJoules puts its exit here, Mines at 49. */
	private static final int[] EXIT_SLOTS = {40, 49};

	/**
	 * What the betting screen is set to.
	 *
	 * @param mines    the mine count, or 0 when this is not the Mines screen
	 * @param hasMinMax whether the server advertises shift-click for min and max
	 * @param exitSlot the exit pane, or -1 when the menu has none
	 */
	public record BetState(long investmentCents, int mines, boolean hasMinMax, int exitSlot) {
		public boolean isMines() {
			return mines > 0;
		}

		public String investmentText() {
			return Money.format(investmentCents);
		}
	}

	private static final Pattern START = Pattern.compile("^\\s*Start\\s+\\w+",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern PLUS_100 = Pattern.compile("^\\s*\\+\\$100\\b");
	private static final Pattern INVESTMENT = Pattern.compile(
			"Investment:\\s*(\\$[\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);
	private static final Pattern MINES = Pattern.compile("Mines:\\s*(\\d+)",
			Pattern.CASE_INSENSITIVE);
	private static final String MIN_HINT = "shift-click for minimum";
	private static final String EXIT = "exit";

	private BetReader() {
	}

	/**
	 * The cheap probe. "Start ..." alone is not distinctive enough for a server with this
	 * many menus, so the stake row's first chip has to be there too.
	 */
	public static boolean looksLikeBet(String startName, String plusHundredName) {
		return startName != null && plusHundredName != null
				&& START.matcher(startName).find()
				&& PLUS_100.matcher(plusHundredName).find();
	}

	public static boolean isBet(List<SlotView> slots) {
		if (slots == null || slots.size() < CasinoPanel.CONTAINER_SLOTS) {
			return false;
		}
		if (!looksLikeBet(SlotView.nameAt(slots, START_SLOT),
				SlotView.nameAt(slots, PLUS_100_SLOT))) {
			return false;
		}
		// The whole stake row, so a half-sent menu is not drawn with dead chips.
		for (int slot = MINUS_10K_SLOT; slot <= PLUS_10K_SLOT; slot++) {
			if (SlotView.nameAt(slots, slot).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	public static BetState read(List<SlotView> slots) {
		SlotView start = SlotView.at(slots, START_SLOT);
		return new BetState(
				// The emerald states both figures, so one slot carries the whole screen.
				find(INVESTMENT, start, true),
				(int) find(MINES, start, false),
				hasMinMax(slots),
				exitSlot(slots));
	}

	/** Which of the two known exit slots this menu actually uses. */
	private static int exitSlot(List<SlotView> slots) {
		for (int slot : EXIT_SLOTS) {
			if (SlotView.nameAt(slots, slot).toLowerCase(Locale.ROOT).contains(EXIT)) {
				return slot;
			}
		}
		return -1;
	}

	private static boolean hasMinMax(List<SlotView> slots) {
		SlotView minus = SlotView.at(slots, MINUS_10K_SLOT);
		return minus != null && minus.loreContains(MIN_HINT);
	}

	/** The first match of {@code pattern} in a slot's lore, as cents or as a plain number. */
	private static long find(Pattern pattern, SlotView slot, boolean money) {
		if (slot == null || slot.lore() == null) {
			return 0L;
		}
		for (String line : slot.lore()) {
			Matcher matcher = pattern.matcher(line == null ? "" : line);
			if (!matcher.find()) {
				continue;
			}
			if (money) {
				return Money.parseCents(matcher.group(1));
			}
			try {
				return Long.parseLong(matcher.group(1));
			} catch (NumberFormatException e) {
				return 0L;
			}
		}
		return 0L;
	}
}
