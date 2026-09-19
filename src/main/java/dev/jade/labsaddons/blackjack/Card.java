package dev.jade.labsaddons.blackjack;

import dev.jade.labsaddons.casino.Glyphs;

/**
 * One card, parsed from the token the server puts in a hand's lore.
 *
 * <p>Each lore line is a rank followed by a lab symbol: {@code 6🔥}, {@code 10⚗},
 * {@code Δ⚗}, or the grey {@code ??} for the hole card. {@code Δ} is the ace — it counted
 * eleven in the window title both times it appeared.
 *
 * <p>The four lab symbols are mapped onto the four playing-card suits so the board can
 * draw a card table. The mapping is arbitrary but fixed, so the same server card always
 * draws the same way:
 *
 * <pre>
 *   ☢ radioactive  →  diamonds
 *   ⚗ alembic      →  hearts
 *   ⚡ bolt         →  spades
 *   🔥 flame        →  clubs
 * </pre>
 *
 * <p>Every ten-value card renders as "10", so jacks, queens and kings are
 * indistinguishable in the data. The board shows what the server said rather than
 * inventing a face card.
 */
public record Card(int rank, Suit suit) {
	/** The ace's value when it is not being demoted. */
	public static final int ACE = 11;
	/** A face-down card: the server states nothing but "??". */
	public static final Card HIDDEN = new Card(0, null);

	public enum Suit {
		DIAMONDS(Glyphs.DIAMOND, true),
		HEARTS(Glyphs.HEART, true),
		SPADES(Glyphs.SPADE, false),
		CLUBS(Glyphs.CLUB, false);

		private final int[] pip;
		private final boolean red;

		Suit(int[] pip, boolean red) {
			this.pip = pip;
			this.red = red;
		}

		public int[] pip() {
			return pip;
		}

		public boolean isRed() {
			return red;
		}
	}

	private static final int SYMBOL_RADIOACTIVE = 0x2622;
	private static final int SYMBOL_ALEMBIC = 0x2697;
	private static final int SYMBOL_BOLT = 0x26A1;
	private static final int SYMBOL_FLAME = 0x1F525;
	private static final char RANK_ACE = 'Δ';
	private static final String FACE_DOWN = "??";

	/**
	 * The card in one lore line, or null when the line is not a card at all.
	 *
	 * <p>Null rather than a throw: this reads a live GUI, and a blank separator line
	 * between cards is ordinary.
	 */
	public static Card parse(String token) {
		if (token == null) {
			return null;
		}
		String text = token.trim();
		if (text.isEmpty()) {
			return null;
		}
		if (text.startsWith(FACE_DOWN)) {
			return HIDDEN;
		}
		int at = 0;
		int rank;
		if (text.charAt(0) == RANK_ACE) {
			rank = ACE;
			at = 1;
		} else {
			int digits = 0;
			while (at < text.length() && Character.isDigit(text.charAt(at))) {
				digits = digits * 10 + (text.charAt(at) - '0');
				at++;
			}
			if (at == 0 || digits < 2 || digits > 10) {
				return null;
			}
			rank = digits;
		}
		Suit suit = suitOf(text, at);
		return suit == null ? null : new Card(rank, suit);
	}

	private static Suit suitOf(String text, int at) {
		if (at >= text.length()) {
			return null;
		}
		// The flame is outside the basic plane, so this has to read a code point rather
		// than a char — charAt would hand back half a surrogate pair.
		return switch (text.codePointAt(at)) {
			case SYMBOL_RADIOACTIVE -> Suit.DIAMONDS;
			case SYMBOL_ALEMBIC -> Suit.HEARTS;
			case SYMBOL_BOLT -> Suit.SPADES;
			case SYMBOL_FLAME -> Suit.CLUBS;
			default -> null;
		};
	}

	public boolean isHidden() {
		return suit == null;
	}

	public boolean isAce() {
		return rank == ACE;
	}

	/** "A", or the rank as the server stated it. */
	public String rankText() {
		if (isHidden()) {
			return "?";
		}
		return isAce() ? "A" : String.valueOf(rank);
	}
}
