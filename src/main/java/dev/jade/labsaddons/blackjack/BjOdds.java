package dev.jade.labsaddons.blackjack;

import java.util.List;
import java.util.Locale;

/**
 * What one more card is likely to do to a hand.
 *
 * <p>Modelled on a full 52-card deck with every ten-value card counted as a ten: four
 * aces, four each of two through nine, and sixteen tens. That is the shape the tokens
 * imply, and the same card turned up in two consecutive captured rounds, so the shoe
 * reshuffles and there is nothing to count. An infinite-deck model is therefore the
 * honest one — it does not pretend to know what has already been dealt.
 *
 * <p>A soft hand cannot bust at all, because its ace demotes to one. The board says 0%
 * there rather than a number that would be wrong.
 */
public final class BjOdds {
	private static final int TARGET = 21;
	private static final int DECK = 52;
	/** Tens, jacks, queens and kings all read as a ten. */
	private static final int TENS = 16;
	private static final int PER_RANK = 4;

	private BjOdds() {
	}

	/**
	 * The chance the next card puts this hand over 21.
	 *
	 * @param total the hand's value as the server states it
	 * @param soft  whether an ace in it is currently counted as eleven
	 */
	public static double bustChance(int total, boolean soft) {
		// A soft hand demotes its ace instead of busting, and nobody draws on 21.
		if (soft || total >= TARGET) {
			return 0.0;
		}
		int busts = 0;
		// An ace never busts a hand either — it comes in as one when eleven would.
		for (int rank = 2; rank <= 10; rank++) {
			if (total + rank > TARGET) {
				busts += rank == 10 ? TENS : PER_RANK;
			}
		}
		return (double) busts / DECK;
	}

	/** Whether the hand is counting an ace as eleven, and so cannot bust. */
	public static boolean isSoft(List<Card> hand, int total) {
		if (hand == null || total <= 0) {
			return false;
		}
		int hard = 0;
		boolean ace = false;
		for (Card card : hand) {
			if (card == null || card.isHidden()) {
				return false;
			}
			if (card.isAce()) {
				ace = true;
				hard += 1;
			} else {
				hard += card.rank();
			}
		}
		return ace && hard + (Card.ACE - 1) == total;
	}

	/** "62%", or "—" when the figure would be meaningless. */
	public static String bustText(int total, boolean soft) {
		if (total <= 0 || total >= TARGET) {
			return "—";
		}
		return String.format(Locale.ROOT, "%.0f%%", bustChance(total, soft) * 100);
	}
}
