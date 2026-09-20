package dev.jade.labsaddons.coinflip;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every string here is verbatim from the capture or from the logs around it. */
class CfChatTest {
	private static final long DOLLAR = 100L;
	private static final String SELF = "Ophiliah";
	private static final long NOW = 1_000_000L;

	@BeforeEach
	void clear() {
		CfChat.reset();
	}

	@Test
	void aPostedFlipBecomesATakeableRow() {
		CfChat.onMessage("Coinflip » Nothing_but_fail has just created a $1,500,000 Coinflip! "
				+ "Click this message or do /cf take 4164 to take it!", SELF, NOW);
		List<CfChat.OpenFlip> open = CfChat.openFlips(NOW);
		assertEquals(1, open.size());
		assertEquals(4164, open.get(0).id());
		assertEquals("Nothing_but_fail", open.get(0).player());
		assertEquals(1_500_000 * DOLLAR, open.get(0).wagerCents());
	}

	@Test
	void ourOwnPostIsNotOfferedBackToUs() {
		CfChat.onMessage("Coinflip » Ophiliah has just created a $1,200,000 Coinflip! "
				+ "Click this message or do /cf take 4170 to take it!", SELF, NOW);
		assertTrue(CfChat.openFlips(NOW).isEmpty());
	}

	@Test
	void theBroadcastFigureIsThePotAndTheRowGoesAnyway() {
		CfChat.onMessage("Coinflip » Nothing_but_fail has just created a $1,500,000 Coinflip! "
				+ "Click this message or do /cf take 4164 to take it!", SELF, NOW);
		// $3,000,000 here is the pot; the row was posted for $1,500,000. Halving it is the
		// only reason this retires the right flip.
		CfChat.onMessage("Coinflip » Nothing_but_fail has just won a $3,000,000 coinflip "
				+ "against Ophiliah! Play with /cf", SELF, NOW);
		assertTrue(CfChat.openFlips(NOW).isEmpty());
	}

	@Test
	void aWinReportsTheWagerAndWhatCameBack() {
		CfChat.Outcome outcome = CfChat.onMessage("\nCoinflip » You have won the $600,000 "
				+ "coinflip against _MikeHunt and received $1,140,000!\n", SELF, NOW);
		assertNotNull(outcome);
		assertTrue(outcome.won());
		assertEquals(600_000 * DOLLAR, outcome.wagerCents());
		assertEquals(1_140_000 * DOLLAR, outcome.returnedCents());
		assertEquals("_MikeHunt", outcome.opponent());
		assertEquals(540_000 * DOLLAR, CfChat.sessionNetCents());
		assertEquals(1, CfChat.sessionPlayed());
		assertEquals(1, CfChat.sessionWon());
	}

	@Test
	void aLossReportsTheWagerAndNothingBack() {
		CfChat.Outcome outcome = CfChat.onMessage("\nCoinflip » You have lost the $1,500,000 "
				+ "coinflip against Nothing_but_fail.\n", SELF, NOW);
		assertNotNull(outcome);
		assertEquals(false, outcome.won());
		assertEquals(1_500_000 * DOLLAR, outcome.wagerCents());
		assertEquals(0L, outcome.returnedCents());
		assertEquals(-1_500_000 * DOLLAR, CfChat.sessionNetCents());
		assertEquals(0, CfChat.sessionWon());
	}

	@Test
	void theBroadcastOfOurOwnFlipIsNotCountedTwice() {
		CfChat.onMessage("\nCoinflip » You have lost the $1,500,000 coinflip against "
				+ "Nothing_but_fail.\n", SELF, NOW);
		assertNull(CfChat.onMessage("Coinflip » Nothing_but_fail has just won a $3,000,000 "
				+ "coinflip against Ophiliah! Play with /cf", SELF, NOW));
		assertEquals(1, CfChat.sessionPlayed());
	}

	@Test
	void takingAFlipInTheLobbyNamesItForTheScreenThatFollows() {
		CfChat.onMessage("Coinflip » _MikeHunt has just created a $600,000 Coinflip! "
				+ "Click this message or do /cf take 4161 to take it!", SELF, NOW);
		CfChat.expect(4161, 600_000 * DOLLAR, "_MikeHunt", "heads", NOW);
		CfChat.Taken taken = CfChat.taken(NOW);
		assertNotNull(taken);
		assertEquals(4161, taken.id());
		assertEquals("_MikeHunt", taken.opponent());
		assertEquals("heads", taken.creatorFace());
		// Ours now, so it is no longer on offer.
		assertTrue(CfChat.openFlips(NOW).isEmpty());
	}

	@Test
	void theDebitLineIsOnlyTrustedForAMoment() {
		CfChat.onMessage("MCLabs » $750 has been taken from your account.", SELF, NOW);
		assertNotNull(CfChat.taken(NOW), "the flip screen opens right behind it");
		// That line is not coinflip's — every purchase on the server sends it — so it must
		// not still be standing in for a wager ten seconds later.
		assertNull(CfChat.taken(NOW + 10_000));
	}

	@Test
	void aRefusedTakeLeavesNothingInFlight() {
		CfChat.onMessage("MCLabs » $20,000,000 has been taken from your account.", SELF, NOW);
		CfChat.onMessage("Coinflip » You do not have enough money to take that coinflip! "
				+ "($20,000,000)", SELF, NOW);
		assertNull(CfChat.taken(NOW));
	}

	@Test
	void aPostDropsOffAfterItsTwentyFourHours() {
		CfChat.onMessage("Coinflip » GonjaMayor has just created a $750 Coinflip! "
				+ "Click this message or do /cf take 4603 to take it!", SELF, NOW);
		assertEquals(1, CfChat.openFlips(NOW + 23 * 3_600_000L).size());
		assertTrue(CfChat.openFlips(NOW + 25 * 3_600_000L).isEmpty());
	}

	/**
	 * Chat is the one input here anybody on the server can shape, and the id used to go
	 * straight to Integer.parseInt — which threw out of the chat dispatcher.
	 */
	@Test
	void anAbsurdFlipIdIsIgnoredRatherThanThrown() {
		CfChat.onMessage("Coinflip » Mallory has just created a $200 Coinflip! "
				+ "Click this message or do /cf take 99999999999 to take it!", SELF, NOW);
		assertTrue(CfChat.openFlips(NOW).isEmpty());
	}

	/**
	 * The broadcast is clickable, so this — not the lobby — is how a flip is usually taken,
	 * and until the click was noticed the flip screen had nothing but a dash on it.
	 */
	@Test
	void takingAFlipFromChatNamesItForTheScreenThatFollows() {
		CfChat.onMessage("Coinflip » _MikeHunt has just created a $600,000 Coinflip! "
				+ "Click this message or do /cf take 4161 to take it!", SELF, NOW);
		CfChat.onCommandSent("/cf take 4161", NOW);
		CfChat.Taken taken = CfChat.taken(NOW);
		assertNotNull(taken);
		assertEquals(4161, taken.id());
		assertEquals(600_000 * DOLLAR, taken.wagerCents());
		assertEquals("_MikeHunt", taken.opponent());
		assertTrue(CfChat.openFlips(NOW).isEmpty(), "ours now, so no longer on offer");
	}

	/** Typed, clicked or macro'd, with or without the slash — all the same take. */
	@Test
	void aTypedTakeReadsTheSameAsAClickedOne() {
		CfChat.onMessage("Coinflip » _MikeHunt has just created a $600,000 Coinflip! "
				+ "Click this message or do /cf take 4161 to take it!", SELF, NOW);
		CfChat.onCommandSent("cf  TAKE  4161", NOW);
		assertEquals(600_000 * DOLLAR, CfChat.taken(NOW).wagerCents());
	}

	/**
	 * A wager of zero is what the board already knows how to say nothing about; a wager
	 * invented for a flip never seen posted is not.
	 */
	@Test
	void aTakeForAFlipWeNeverSawIsNotGuessedAt() {
		CfChat.onCommandSent("/cf take 4161", NOW);
		assertNull(CfChat.taken(NOW));
	}

	@Test
	void aTakeCommandWithAnAbsurdIdIsIgnoredRatherThanThrown() {
		CfChat.onCommandSent("/cf take 99999999999", NOW);
		assertNull(CfChat.taken(NOW));
	}

	/**
	 * The debit line stands in for the wager when nothing better is known. It used to be
	 * dropped whenever any earlier take was still on the books, and a lobby click whose
	 * result line never arrived stays on them for a minute.
	 */
	@Test
	void aStaleTakeDoesNotSwallowTheNextFlipsDebit() {
		CfChat.expect(4161, 600_000 * DOLLAR, "_MikeHunt", "heads", NOW);
		long later = NOW + 61_000L;
		CfChat.onMessage("MCLabs » $750 has been taken from your account.", SELF, later);
		assertEquals(750 * DOLLAR, CfChat.taken(later).wagerCents());
	}

	@Test
	void theLongestRealisticFlipIdStillReads() {
		CfChat.onMessage("Coinflip » Mallory has just created a $200 Coinflip! "
				+ "Click this message or do /cf take 999999999 to take it!", SELF, NOW);
		assertEquals(999_999_999, CfChat.openFlips(NOW).get(0).id());
	}
}
