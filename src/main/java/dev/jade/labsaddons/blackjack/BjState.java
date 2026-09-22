package dev.jade.labsaddons.blackjack;

import dev.jade.labsaddons.casino.Money;

import java.util.List;

/**
 * One frame's reading of the BondJoules menu.
 *
 * @param yourTotal the total the server states in the window title, or 0 before it does
 * @param banner    the status band's own words, shown verbatim once the hand is settled
 */
public record BjState(long stakeCents, int yourTotal, int labTotal,
		List<Card> yourHand, List<Card> labHand, Phase phase, String banner,
		boolean canHit, boolean canStand, boolean canDouble) {

	public enum Phase {
		/** Cards still going down; no controls yet. */
		DEALING,
		/** "Energize or Finalize?" — your move. */
		YOUR_TURN,
		/** The lab is drawing. */
		LAB_TURN,
		WON,
		LOST,
		PUSH
	}

	/** Whether the hand is over, whichever way it went. */
	public boolean settled() {
		return phase == Phase.WON || phase == Phase.LOST || phase == Phase.PUSH;
	}

	/** Whether your ace is being counted as eleven, and so the hand cannot bust. */
	public boolean soft() {
		return BjOdds.isSoft(yourHand, yourTotal);
	}

	public String bustText() {
		return BjOdds.bustText(yourTotal, soft());
	}

	public String stakeText() {
		return Money.format(stakeCents);
	}
}
