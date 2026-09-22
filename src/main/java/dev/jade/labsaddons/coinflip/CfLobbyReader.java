package dev.jade.labsaddons.coinflip;

import dev.jade.labsaddons.casino.Money;
import dev.jade.labsaddons.casino.SlotView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the "Active Coinflips" menu — forty-five slots, of which the first thirty-six are
 * the posted flips.
 *
 * <p>The head's own name carries the id: {@code Blackbill67 (#4566)}. That is what makes a
 * flip nameable outside its slot, and it is how the announcement in chat and the row in the
 * chest are known to be the same flip.
 */
public final class CfLobbyReader {
	/** Posted flips fill these, left to right, and there is no second page. */
	public static final int FLIP_SLOTS = 36;
	public static final int CREATE_SLOT = 37;
	public static final int INFO_SLOT = 38;
	public static final int REFRESH_SLOT = 40;

	/** One posted flip. {@code creatorFace} is the side the poster claimed. */
	public record Row(int slot, int id, String player, long wagerCents, String creatorFace,
			String created) {
		/**
		 * The side you take, which is whichever one they did not — or "" when the server
		 * did not say, so the board shows nothing rather than a coin toss of its own.
		 */
		public String yourFace() {
			if (creatorFace == null || creatorFace.isEmpty()) {
				return "";
			}
			return "heads".equalsIgnoreCase(creatorFace) ? "tails" : "heads";
		}
	}

	/** The menu, with the bounds the book at slot 38 states. */
	public record Lobby(List<Row> rows, long minCents, long maxCents, String expiry) {
	}

	/**
	 * The id is bounded rather than {@code \d+} so {@link Integer#parseInt} below cannot
	 * throw on a head whose name the server has put an absurd figure in — this runs inside
	 * the board's draw, where an exception is a crash on every frame the row is on screen.
	 * A name that does not match is skipped, which is what {@link #row} already does with
	 * any head it does not recognise.
	 */
	private static final Pattern NAMED = Pattern.compile("^(\\w{1,16})\\s*\\(#(\\d{1,9})\\)$");
	private static final Pattern WAGER = Pattern.compile(
			"^\\s*Wager:\\s*(\\$[\\d,]+(?:\\.\\d{1,2})?)\\s*$", Pattern.CASE_INSENSITIVE);
	private static final Pattern FACE = Pattern.compile(
			"^\\s*Face:\\s*(\\w+)\\s*$", Pattern.CASE_INSENSITIVE);
	private static final Pattern CREATED = Pattern.compile(
			"^\\s*Created:\\s*(.+?)\\s*$", Pattern.CASE_INSENSITIVE);
	private static final Pattern MINIMUM = Pattern.compile(
			"Minimum Wager:\\s*(\\$[\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);
	private static final Pattern MAXIMUM = Pattern.compile(
			"Maximum Wager:\\s*(\\$[\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);
	private static final Pattern EXPIRY = Pattern.compile(
			"Expiry Time:\\s*(.+?)\\s*$", Pattern.CASE_INSENSITIVE);

	private CfLobbyReader() {
	}

	/**
	 * The cheap probe: the book's name alone. Nothing else in the game is called this, so
	 * no title is needed to be sure.
	 */
	public static boolean looksLikeLobby(String infoName, String refreshName) {
		return contains(infoName, "Coinflip Information")
				|| (contains(infoName, "Coinflip") && contains(refreshName, "Refresh"));
	}

	/** The lobby's own title, which is all the once-per-open stats sync needs to see. */
	public static boolean isLobbyTitle(String title) {
		return contains(title, "Active Coinflips");
	}

	public static boolean isLobby(List<SlotView> slots) {
		return looksLikeLobby(SlotView.nameAt(slots, INFO_SLOT),
				SlotView.nameAt(slots, REFRESH_SLOT));
	}

	public static Lobby read(List<SlotView> slots) {
		List<Row> rows = new ArrayList<>();
		for (int slot = 0; slot < FLIP_SLOTS; slot++) {
			Row row = row(SlotView.at(slots, slot));
			if (row != null) {
				rows.add(row);
			}
		}
		SlotView book = SlotView.at(slots, INFO_SLOT);
		return new Lobby(List.copyOf(rows), lore(book, MINIMUM, 0L), lore(book, MAXIMUM, 0L),
				expiry(book));
	}

	private static Row row(SlotView slot) {
		if (slot == null || slot.isEmpty()) {
			return null;
		}
		Matcher named = NAMED.matcher(slot.name().trim());
		if (!named.matches()) {
			// A decorative pane, or a head whose name the server has changed shape on.
			// Either way it is not a flip, and inventing one would put a wager on screen
			// that nobody posted.
			return null;
		}
		long wager = 0L;
		String face = "";
		String created = "";
		for (String line : slot.lore()) {
			Matcher wagerLine = WAGER.matcher(line);
			if (wagerLine.matches()) {
				wager = Money.parseCents(wagerLine.group(1));
				continue;
			}
			Matcher faceLine = FACE.matcher(line);
			if (faceLine.matches()) {
				face = faceLine.group(1).toLowerCase(Locale.ROOT);
				continue;
			}
			Matcher createdLine = CREATED.matcher(line);
			if (createdLine.matches()) {
				created = createdLine.group(1);
			}
		}
		if (wager <= 0L) {
			return null;
		}
		return new Row(slot.index(), Integer.parseInt(named.group(2)), named.group(1), wager,
				face, created);
	}

	private static long lore(SlotView slot, Pattern pattern, long fallback) {
		if (slot == null) {
			return fallback;
		}
		for (String line : slot.lore()) {
			Matcher matcher = pattern.matcher(line);
			if (matcher.find()) {
				return Money.parseCents(matcher.group(1));
			}
		}
		return fallback;
	}

	private static String expiry(SlotView slot) {
		if (slot == null) {
			return "";
		}
		for (String line : slot.lore()) {
			Matcher matcher = EXPIRY.matcher(line);
			if (matcher.find()) {
				return matcher.group(1);
			}
		}
		return "";
	}

	private static boolean contains(String text, String needle) {
		return text != null
				&& text.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
	}
}
