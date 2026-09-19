package dev.jade.labsaddons.blackjack;

import dev.jade.labsaddons.casino.CasinoPanel;
import dev.jade.labsaddons.casino.Money;
import dev.jade.labsaddons.casino.SlotView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the BondJoules container into a {@link BjState}.
 *
 * <p>Two things make this cheaper than it looks. The window title states the stake and
 * both totals outright — {@code BondJoules ($3,100) [16 - 10]} — so no card arithmetic is
 * needed for the big numbers. And each hand is a single item whose lore is one card per
 * line, so the whole table is two slots.
 *
 * <p>The eighteen-pane status band is the only phase signal. The buttons are not: they
 * vanish for a beat mid-deal while the band still reads "Starting experiment...".
 */
public final class BjReader {
	/** The head that marks the lab's seat. */
	public static final int LAB_SEAT_SLOT = 4;
	/** The head that marks yours. */
	public static final int YOUR_SEAT_SLOT = 49;
	public static final int LAB_HAND_SLOT = 13;
	public static final int YOUR_HAND_SLOT = 40;
	/** First pane of the status band; all eighteen say the same thing. */
	public static final int BANNER_SLOT = 18;
	public static final int DOUBLE_SLOT = 45;
	public static final int STAND_SLOT = 47;
	public static final int HIT_SLOT = 51;

	private static final String LAB_SEAT = "competing lab";
	private static final String YOUR_SEAT = "you";
	private static final String HIT_NAME = "energize";
	private static final String STAND_NAME = "finalize";
	private static final String DOUBLE_NAME = "double down";

	/** "BondJoules ($3,100) [16 - 10]" — the bracket is absent until the deal starts. */
	private static final Pattern TITLE = Pattern.compile(
			"\\((\\$[\\d,]+(?:\\.\\d{1,2})?)\\)(?:\\s*\\[\\s*(\\d+)\\s*-\\s*(\\d+)\\s*])?");

	private BjReader() {
	}

	/**
	 * The cheap probe: the two seat heads. Two slot names, because this runs against every
	 * container screen in the game twice a frame.
	 */
	public static boolean looksLikeBlackjack(String labSeatName, String yourSeatName) {
		return labSeatName != null && yourSeatName != null
				&& labSeatName.toLowerCase(Locale.ROOT).contains(LAB_SEAT)
				&& yourSeatName.trim().equalsIgnoreCase(YOUR_SEAT);
	}

	/**
	 * Whether these slots are the BondJoules menu: both seats, plus a status band saying
	 * something. The band is what every other reading keys off, so a container without one
	 * is not yet worth drawing.
	 */
	public static boolean isBlackjack(List<SlotView> slots) {
		if (slots == null || slots.size() < CasinoPanel.CONTAINER_SLOTS) {
			return false;
		}
		return looksLikeBlackjack(SlotView.nameAt(slots, LAB_SEAT_SLOT),
				SlotView.nameAt(slots, YOUR_SEAT_SLOT))
				&& !SlotView.nameAt(slots, BANNER_SLOT).isEmpty();
	}

	/**
	 * The menu's title without the live totals the server appends — "BondJoules ($3,100)"
	 * out of "BondJoules ($3,100) [16 - 10]". Used to tell a reopen of the same hand from
	 * a different menu opening, which the bracket would otherwise hide.
	 */
	public static String menuKey(String title) {
		String text = title == null ? "" : title;
		int bracket = text.indexOf(" [");
		return bracket < 0 ? text : text.substring(0, bracket);
	}

	public static BjState read(List<SlotView> slots, String title) {
		String banner = SlotView.nameAt(slots, BANNER_SLOT);
		List<Card> yourHand = hand(SlotView.at(slots, YOUR_HAND_SLOT));
		List<Card> labHand = hand(SlotView.at(slots, LAB_HAND_SLOT));
		Matcher matched = TITLE.matcher(title == null ? "" : title);
		boolean parsed = matched.find();

		return new BjState(
				parsed ? Money.parseCents(matched.group(1)) : 0L,
				total(parsed ? matched.group(2) : null, SlotView.at(slots, YOUR_HAND_SLOT)),
				total(parsed ? matched.group(3) : null, SlotView.at(slots, LAB_HAND_SLOT)),
				yourHand, labHand, phase(banner), banner,
				named(slots, HIT_SLOT, HIT_NAME),
				named(slots, STAND_SLOT, STAND_NAME),
				named(slots, DOUBLE_SLOT, DOUBLE_NAME));
	}

	/**
	 * The phase, from the band's words. Checked most-final first: a settled band and a
	 * live one never appear together, but the order makes that impossible to get wrong.
	 */
	static BjState.Phase phase(String banner) {
		String text = banner == null ? "" : banner.toLowerCase(Locale.ROOT);
		if (text.contains("lost")) {
			return BjState.Phase.LOST;
		}
		if (text.contains("neutralized")) {
			return BjState.Phase.PUSH;
		}
		// "Perfect Reaction! You win $7,750!" is the only win seen; match on either half
		// so a reworded prefix does not lose the result.
		if (text.contains("you win") || text.contains("perfect reaction")) {
			return BjState.Phase.WON;
		}
		if (text.contains("experimenting")) {
			return BjState.Phase.LAB_TURN;
		}
		if (text.contains("energize") || text.contains("finalize")) {
			return BjState.Phase.YOUR_TURN;
		}
		return BjState.Phase.DEALING;
	}

	/** The cards in one hand item's lore, in the order the server listed them. */
	static List<Card> hand(SlotView slot) {
		if (slot == null || slot.lore() == null) {
			return List.of();
		}
		List<Card> cards = new ArrayList<>(slot.lore().size());
		for (String line : slot.lore()) {
			Card card = Card.parse(line);
			if (card != null) {
				cards.add(card);
			}
		}
		return List.copyOf(cards);
	}

	/**
	 * A total from the title, falling back to the hand item's stack count — the server
	 * states it both ways, and the title is preferred because it is there even when the
	 * hand slot is mid-update.
	 */
	private static int total(String fromTitle, SlotView hand) {
		if (fromTitle != null) {
			try {
				return Integer.parseInt(fromTitle);
			} catch (NumberFormatException ignored) {
				// Fall through to the stack count.
			}
		}
		return hand == null ? 0 : hand.count();
	}

	private static boolean named(List<SlotView> slots, int index, String expected) {
		return SlotView.nameAt(slots, index).toLowerCase(Locale.ROOT).contains(expected);
	}
}
