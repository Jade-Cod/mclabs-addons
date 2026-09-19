package dev.jade.labsaddons.double2;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the Double² container into a {@link D2State}.
 *
 * <p>Takes {@link SlotView}s rather than a {@code HandledScreen} so every lore form
 * below can be unit-tested — the same Minecraft-free seam
 * {@code SunkenTreasureReader.cratesLeft} uses.
 *
 * <p>Unlike every other GUI reader in the mod, this one runs <b>every frame</b>. The
 * once-per-open guard in {@code LabsAddonsClient} exists because lore elsewhere is a
 * static snapshot; here the lore changing is the entire point.
 */
public final class D2Reader {
	/** One container slot, flattened. */
	public record SlotView(int index, String name, List<String> lore, int count) {
		public String loreLine(int i) {
			return lore != null && i < lore.size() ? lore.get(i) : "";
		}

		public boolean loreContains(String needle) {
			if (lore == null) {
				return false;
			}
			String lower = needle.toLowerCase(Locale.ROOT);
			for (String line : lore) {
				if (line != null && line.toLowerCase(Locale.ROOT).contains(lower)) {
					return true;
				}
			}
			return false;
		}
	}

	public static final int CONTAINER_SLOTS = 54;
	/** Clicking any pane of the banner row invests; 27 is the left-most. */
	public static final int INVEST_SLOT = 27;
	private static final int PICK_FIRST = 2;
	private static final int PICK_LAST = 6;
	public static final int CHIP_FIRST = 11;
	public static final int CHIP_LAST = 15;
	private static final int BANNER_FIRST = 27;
	private static final int BANNER_LAST = 35;
	private static final int YOUR_BET_SLOT = 31;
	private static final int DROUGHT_SLOT = 34;
	private static final int CLOCK_SLOT = 40;
	static final int WHEEL_FIRST = 45;

	private static final Pattern SECONDS = Pattern.compile("closing in (\\d+) second",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern CURRENT_INVESTMENT = Pattern.compile(
			"current investment:\\s*\\$([\\d,]+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern YOUR_BET = Pattern.compile(
			"\\$([\\d,]+)\\s+in\\s+([A-Za-z]{3})", Pattern.CASE_INSENSITIVE);
	private static final Pattern POT_LINE = Pattern.compile(
			"^\\s*([A-Za-z]{3}):\\s*\\$([\\d,]+)\\s*$");
	private static final Pattern INVESTORS = Pattern.compile("(\\d+)\\s+player",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern ROUNDS_AGO = Pattern.compile("(\\d+)\\s+rounds?\\s+ago",
			Pattern.CASE_INSENSITIVE);

	private D2Reader() {
	}

	/**
	 * Whether these slots are the Double² menu.
	 *
	 * <p>Recognised by its contents, not its title: the five labs sit in slots 2–6 in a
	 * fixed order in every phase. Same approach as {@code SunkenTreasureReader}, and it
	 * survives the menu being renamed.
	 */
	public static boolean isDouble2(List<SlotView> slots) {
		if (slots == null || slots.size() < CONTAINER_SLOTS) {
			return false;
		}
		Lab[] expected = Lab.values();
		for (int i = 0; i < expected.length; i++) {
			SlotView slot = slots.get(PICK_FIRST + i);
			if (slot == null || Lab.fromText(slot.name()) != expected[i]) {
				return false;
			}
		}
		// The wheel row has to be there too. The server fills the menu a slot at a time,
		// so the labs can land a frame or two before the wheel does — and a board drawn
		// in that gap has nothing to put in its middle. Waiting for the whole thing keeps
		// the vanilla menu up for those frames instead of showing an empty wheel.
		for (int i = 0; i < D2Ring.WINDOW; i++) {
			SlotView slot = slots.get(WHEEL_FIRST + i);
			if (slot == null || Lab.fromText(slot.name()) == null) {
				return false;
			}
		}
		return true;
	}

	public static D2State read(List<SlotView> slots) {
		Lab settled = settledLab(slots);
		D2State.Phase phase = phase(slots, settled);

		return new D2State(phase, seconds(slots), selected(slots), stake(slots),
				betLab(slots, phase), betAmount(slots, phase), pot(slots), investors(slots),
				ringOffset(slots), settled, drought(slots));
	}

	/**
	 * The banner row names the profiting lab once the wheel stops — nine panes whose name
	 * is the bare code. Checked before the clock, because the clock still reads
	 * "Simulating market..." for the two seconds the result is up.
	 */
	private static Lab settledLab(List<SlotView> slots) {
		SlotView first = at(slots, BANNER_FIRST);
		SlotView last = at(slots, BANNER_LAST);
		if (first == null || last == null) {
			return null;
		}
		Lab lab = Lab.fromExactCode(first.name());
		return lab != null && lab == Lab.fromExactCode(last.name()) ? lab : null;
	}

	private static D2State.Phase phase(List<SlotView> slots, Lab settled) {
		if (settled != null) {
			return D2State.Phase.SETTLED;
		}
		SlotView clock = at(slots, CLOCK_SLOT);
		String name = clock == null || clock.name() == null
				? "" : clock.name().toLowerCase(Locale.ROOT);
		return name.contains("simulating") ? D2State.Phase.SPINNING : D2State.Phase.BETTING;
	}

	private static int seconds(List<SlotView> slots) {
		SlotView clock = at(slots, CLOCK_SLOT);
		if (clock == null || clock.lore() == null) {
			return -1;
		}
		for (String line : clock.lore()) {
			Matcher matcher = SECONDS.matcher(line == null ? "" : line);
			if (matcher.find()) {
				return parseInt(matcher.group(1));
			}
		}
		return -1;
	}

	/** The lab you have picked: its lore flips from "Select CQL." to "Investing in CQL.". */
	private static Lab selected(List<SlotView> slots) {
		for (int i = PICK_FIRST; i <= PICK_LAST; i++) {
			SlotView slot = at(slots, i);
			if (slot != null && slot.loreContains("investing in")) {
				return Lab.fromText(slot.name());
			}
		}
		return null;
	}

	/** The staked figure, which the server repeats on all five chips. */
	private static long stake(List<SlotView> slots) {
		for (int i = CHIP_FIRST; i <= CHIP_LAST; i++) {
			SlotView slot = at(slots, i);
			if (slot == null || slot.lore() == null) {
				continue;
			}
			for (String line : slot.lore()) {
				Matcher matcher = CURRENT_INVESTMENT.matcher(line == null ? "" : line);
				if (matcher.find()) {
					return parseMoney(matcher.group(1));
				}
			}
		}
		return 0L;
	}

	/**
	 * Slot 31 carries "Your Investment" / "$100,000 in EOL" once you have invested — but
	 * only while betting is open. It is swallowed by the banner in the other phases, so
	 * the locked bet is read from the pot then instead.
	 */
	private static Lab betLab(List<SlotView> slots, D2State.Phase phase) {
		Matcher matcher = yourBet(slots, phase);
		return matcher == null ? null : Lab.fromText(matcher.group(2));
	}

	private static long betAmount(List<SlotView> slots, D2State.Phase phase) {
		Matcher matcher = yourBet(slots, phase);
		return matcher == null ? 0L : parseMoney(matcher.group(1));
	}

	private static Matcher yourBet(List<SlotView> slots, D2State.Phase phase) {
		if (phase != D2State.Phase.BETTING) {
			return null;
		}
		SlotView slot = at(slots, YOUR_BET_SLOT);
		if (slot == null || slot.lore() == null) {
			return null;
		}
		for (String line : slot.lore()) {
			Matcher matcher = YOUR_BET.matcher(line == null ? "" : line);
			if (matcher.find() && Lab.fromText(matcher.group(2)) != null) {
				return matcher;
			}
		}
		return null;
	}

	/**
	 * Per-lab staked totals. Carried by a book in the banner row while betting, and by
	 * every pane of it during the spin, so the first slot that has the lines wins.
	 */
	private static Map<Lab, Long> pot(List<SlotView> slots) {
		Map<Lab, Long> pot = new EnumMap<>(Lab.class);
		for (int i = BANNER_FIRST; i <= BANNER_LAST; i++) {
			SlotView slot = at(slots, i);
			if (slot == null || slot.lore() == null) {
				continue;
			}
			Map<Lab, Long> found = new EnumMap<>(Lab.class);
			for (String line : slot.lore()) {
				Matcher matcher = POT_LINE.matcher(line == null ? "" : line);
				if (!matcher.matches()) {
					continue;
				}
				Lab lab = Lab.fromExactCode(matcher.group(1));
				if (lab != null) {
					found.put(lab, parseMoney(matcher.group(2)));
				}
			}
			if (found.size() == Lab.values().length) {
				return found;
			}
		}
		return pot;
	}

	private static int investors(List<SlotView> slots) {
		for (int i = BANNER_FIRST; i <= BANNER_LAST; i++) {
			SlotView slot = at(slots, i);
			if (slot == null) {
				continue;
			}
			int fromName = firstMatch(INVESTORS, slot.name());
			if (fromName >= 0) {
				return fromName;
			}
			if (slot.lore() != null) {
				for (String line : slot.lore()) {
					int fromLore = firstMatch(INVESTORS, line);
					if (fromLore >= 0) {
						return fromLore;
					}
				}
			}
		}
		return 0;
	}

	/** Where the visible nine sit on the ring, or -1 if they can't be placed. */
	private static int ringOffset(List<SlotView> slots) {
		List<Lab> window = new ArrayList<>(D2Ring.WINDOW);
		for (int i = 0; i < D2Ring.WINDOW; i++) {
			SlotView slot = at(slots, WHEEL_FIRST + i);
			window.add(slot == null ? null : Lab.fromText(slot.name()));
		}
		return D2Ring.lockOn(window);
	}

	/**
	 * Rounds since EOL last profited. The server states it two ways — the lore and the
	 * item's stack count — and the lore is preferred because a count above 64 would be
	 * clamped.
	 */
	private static int drought(List<SlotView> slots) {
		SlotView slot = at(slots, DROUGHT_SLOT);
		if (slot == null) {
			return -1;
		}
		if (slot.lore() != null) {
			for (String line : slot.lore()) {
				int rounds = firstMatch(ROUNDS_AGO, line);
				if (rounds >= 0) {
					return rounds;
				}
			}
		}
		return -1;
	}

	private static int firstMatch(Pattern pattern, String text) {
		if (text == null) {
			return -1;
		}
		Matcher matcher = pattern.matcher(text);
		return matcher.find() ? parseInt(matcher.group(1)) : -1;
	}

	private static SlotView at(List<SlotView> slots, int index) {
		return index < slots.size() ? slots.get(index) : null;
	}

	static long parseMoney(String digits) {
		try {
			return Long.parseLong(digits.replace(",", ""));
		} catch (NumberFormatException e) {
			return 0L;
		}
	}

	private static int parseInt(String digits) {
		try {
			return Integer.parseInt(digits);
		} catch (NumberFormatException e) {
			return -1;
		}
	}
}
