package dev.jade.labsaddons.mastery;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Message strings are verbatim from MCLabs chat reactions. */
public class MasteryChatTrackerTest {
	private static final String WIN =
			"» Jade typed the message in 1 minute and 38.005 seconds and won $7,500!";
	private static final String RUNNER_UP =
			"» Runner-up, 18.336 seconds too late! Earned $2,000!";
	private static final String SPEED = "» Speed: 7WPM.";
	private static final String BOUNTY_FOUND =
			"Bounty » Ophiliah has found a bounty chest near the Airport Terminal in 00:51!"
					+ " There are 5 bounty chests left hidden in Spawn. Use /bounty track in Spawn to track one.";
	/** A real four-deep result; newlines are real in-game, escaped only in the logs. */
	private static final String STANDINGS = " Top players:\n #1. Kojee53 - 131 score\n"
			+ " #2. 011404110 - 95 score\n #3. SaltyHyper - 78 score\n #4. Ophiliah - 78 score\n"
			+ "Use /claim to claim your rewards!";
	/** The deepest real standings seen: exactly nine places, hence Top 3 and Top 9. */
	private static final String FULL_STANDINGS = " Top players:\n #1. SheenTheBean3 - 3,777,444 score\n"
			+ " #2. elaine128 - 1,094,736 score\n #3. fayebeeann - 1,060,778 score\n"
			+ " #4. SquiddyCat - 857,420 score\n #5. BuzzyyyBuzz - 816,242 score\n"
			+ " #6. ThrowbackTo1985 - 734,155 score\n #7. elllla - 332,318 score\n"
			+ " #8. lumpy5983 - 256,214 score\n #9. itfolds69 - 106,389 score\n"
			+ "Use /claim to claim your rewards!";
	/** Posted every few minutes while the event runs — a board, not a result. */
	private static final String LIVE_BOARD = "Mini-Event » King Of The Hill mini-event:"
			+ " Control hills at /minievent koth\nCurrent top players:\n#1. iLikeCatsDotCom\n"
			+ "#2. Stwas\n#3. SupAnimations\n#4. BulletGeological\n#5. MissPatient\n"
			+ "NEW Reach 50 score for 0.25 Event Points!\nMini-event ends in 09:29\nMore info: /minievent";

	@AfterEach
	public void reset() {
		MasteryTracker.clear();
		MasteryGains.clear();
	}

	private static void active(String... names) {
		MasteryTracker.setQuests(java.util.Arrays.stream(names)
				.map(n -> new MasteryQuest(null, n, 0, 100, 0))
				.toList());
	}

	private static double currentOf(String name) {
		return MasteryTracker.quests().stream()
				.filter(q -> q.name().equals(name))
				.findFirst().orElseThrow().current();
	}

	@Test
	public void winAdvancesBothQuests() {
		active(MasteryChatTracker.WIN_QUEST, MasteryChatTracker.COMPLETE_QUEST);
		MasteryChatTracker.onMessage(WIN, "Jade");
		assertEquals(1, currentOf(MasteryChatTracker.WIN_QUEST));
		assertEquals(1, currentOf(MasteryChatTracker.COMPLETE_QUEST), "winning also completes");
	}

	/** The win line is broadcast to everyone, so someone else's win must not count. */
	@Test
	public void otherPlayersWinIsIgnored() {
		active(MasteryChatTracker.WIN_QUEST, MasteryChatTracker.COMPLETE_QUEST);
		MasteryChatTracker.onMessage(
				"» FantasyTagz typed the message in 12.400 seconds and won $7,500!", "Jade");
		assertEquals(0, currentOf(MasteryChatTracker.WIN_QUEST));
		assertEquals(0, currentOf(MasteryChatTracker.COMPLETE_QUEST));
	}

	@Test
	public void runnerUpAdvancesCompleteOnly() {
		active(MasteryChatTracker.WIN_QUEST, MasteryChatTracker.COMPLETE_QUEST);
		MasteryChatTracker.onMessage(RUNNER_UP, "Jade");
		assertEquals(0, currentOf(MasteryChatTracker.WIN_QUEST), "runner-up is not a win");
		assertEquals(1, currentOf(MasteryChatTracker.COMPLETE_QUEST));
	}

	/** The trailing speed line must not double-count the reaction. */
	@Test
	public void speedLineIsInert() {
		active(MasteryChatTracker.COMPLETE_QUEST);
		MasteryChatTracker.onMessage(SPEED, "Jade");
		assertEquals(0, currentOf(MasteryChatTracker.COMPLETE_QUEST));
	}

	/** The whole point of the active check: an unselected challenge earns nothing. */
	@Test
	public void inactiveQuestIsNotInvented() {
		active(MasteryChatTracker.COMPLETE_QUEST);
		MasteryChatTracker.onMessage(WIN, "Jade");
		assertEquals(1, currentOf(MasteryChatTracker.COMPLETE_QUEST));
		assertTrue(MasteryTracker.quests().stream()
				.noneMatch(q -> q.name().equals(MasteryChatTracker.WIN_QUEST)),
				"Win quest was not active, so it must not appear");
	}

	@Test
	public void nothingActiveIsANoOp() {
		MasteryTracker.clear();
		MasteryChatTracker.onMessage(WIN, "Jade");
		MasteryChatTracker.onMessage(RUNNER_UP, "Jade");
		assertFalse(MasteryTracker.hasData());
	}

	@Test
	public void winMatchIsCaseInsensitiveOnName() {
		active(MasteryChatTracker.WIN_QUEST);
		MasteryChatTracker.onMessage(WIN, "jade");
		assertEquals(1, currentOf(MasteryChatTracker.WIN_QUEST));
	}

	/** Short-form time ("38.005 seconds", no minutes) must parse too. */
	@Test
	public void winParsesSecondsOnlyTimeFormat() {
		active(MasteryChatTracker.WIN_QUEST);
		MasteryChatTracker.onMessage("» Jade typed the message in 38.005 seconds and won $7,500!", "Jade");
		assertEquals(1, currentOf(MasteryChatTracker.WIN_QUEST));
	}

	@Test
	public void unknownSelfNameNeverCountsAWin() {
		active(MasteryChatTracker.WIN_QUEST);
		MasteryChatTracker.onMessage(WIN, null);
		assertEquals(0, currentOf(MasteryChatTracker.WIN_QUEST));
	}

	/** Repeated reactions accumulate and the percent tracks the local bump. */
	@Test
	public void repeatedWinsAccumulateAndRecomputePercent() {
		MasteryTracker.setQuests(List.of(
				new MasteryQuest(null, MasteryChatTracker.WIN_QUEST, 37, 100, 37)));
		MasteryChatTracker.onMessage(WIN, "Jade");
		MasteryChatTracker.onMessage(WIN, "Jade");
		assertEquals(39, currentOf(MasteryChatTracker.WIN_QUEST));
		assertEquals(39, MasteryTracker.quests().getFirst().percent());
	}

	// --- the return value drives persistence: a false here means the board is not saved ---

	@Test
	public void winReportsAnAdvanceSoTheBoardIsSaved() {
		active(MasteryChatTracker.WIN_QUEST, MasteryChatTracker.COMPLETE_QUEST);
		assertTrue(MasteryChatTracker.onMessage(WIN, "Jade"));
	}

	/** Only "Complete" selected: the win still advances it, so it still needs saving. */
	@Test
	public void winReportsAnAdvanceWhenOnlyCompleteIsActive() {
		active(MasteryChatTracker.COMPLETE_QUEST);
		assertTrue(MasteryChatTracker.onMessage(WIN, "Jade"));
	}

	@Test
	public void runnerUpReportsAnAdvance() {
		active(MasteryChatTracker.COMPLETE_QUEST);
		assertTrue(MasteryChatTracker.onMessage(RUNNER_UP, "Jade"));
	}

	/** Nothing changed, so nothing should be written to disk. */
	@Test
	public void unrelatedMessageReportsNoAdvance() {
		active(MasteryChatTracker.WIN_QUEST, MasteryChatTracker.COMPLETE_QUEST);
		assertFalse(MasteryChatTracker.onMessage(SPEED, "Jade"));
		assertFalse(MasteryChatTracker.onMessage(
				"» FantasyTagz typed the message in 12.400 seconds and won $7,500!", "Jade"));
	}

	@Test
	public void inactiveQuestReportsNoAdvance() {
		active("Kill Nyx");
		assertFalse(MasteryChatTracker.onMessage(WIN, "Jade"));
		assertFalse(MasteryChatTracker.onMessage(RUNNER_UP, "Jade"));
	}

	// --- bounties: verbatim MCLabs lines, including the local player's own finds ---

	@Test
	public void ownBountyFindAdvancesTheQuest() {
		active(MasteryChatTracker.BOUNTY_QUEST);
		assertTrue(MasteryChatTracker.onMessage(BOUNTY_FOUND, "Ophiliah"));
		assertEquals(1, currentOf(MasteryChatTracker.BOUNTY_QUEST));
	}

	/** Closing out the hunt reads "the last bounty chest" and still counts. */
	@Test
	public void theLastBountyChestAlsoCounts() {
		active(MasteryChatTracker.BOUNTY_QUEST);
		assertTrue(MasteryChatTracker.onMessage(
				"Bounty » Ophiliah has found the last bounty chest near the MCL Corp HQ in 01:11!", "Ophiliah"));
		assertEquals(1, currentOf(MasteryChatTracker.BOUNTY_QUEST));
	}

	/** The find is broadcast to everyone, so someone else's chest must not count. */
	@Test
	public void otherPlayersBountyFindIsIgnored() {
		active(MasteryChatTracker.BOUNTY_QUEST);
		assertFalse(MasteryChatTracker.onMessage(
				"Bounty » _Froid_ has found a bounty chest near the Beachfront in 07:42!"
						+ " There are 4 bounty chests left hidden in Spawn.", "Ophiliah"));
		assertEquals(0, currentOf(MasteryChatTracker.BOUNTY_QUEST));
	}

	/** The hunt-start and hunt-end announcements name no finder and must stay inert. */
	@Test
	public void bountyHuntNoticesAreInert() {
		active(MasteryChatTracker.BOUNTY_QUEST);
		assertFalse(MasteryChatTracker.onMessage("Bounty » Bounty Hunt has started!"
				+ " 6 chests each with 27 stacks of Chorberrium have been hidden around Spawn!", "Ophiliah"));
		assertFalse(MasteryChatTracker.onMessage(
				"Bounty » All bounty chests have been found! Bounty hunt has ended.", "Ophiliah"));
		assertEquals(0, currentOf(MasteryChatTracker.BOUNTY_QUEST));
	}

	// --- mini-event placement: the concluding standings list exactly nine places ---

	@Test
	public void firstPlaceCreditsEveryTier() {
		active(MasteryChatTracker.EVENT_WINNER, MasteryChatTracker.EVENT_TOP_3, MasteryChatTracker.EVENT_TOP_9);
		assertTrue(MasteryChatTracker.onMessage(STANDINGS, "Kojee53"));
		assertEquals(1, currentOf(MasteryChatTracker.EVENT_WINNER));
		assertEquals(1, currentOf(MasteryChatTracker.EVENT_TOP_3), "first is also in the top 3");
		assertEquals(1, currentOf(MasteryChatTracker.EVENT_TOP_9), "and in the top 9");
	}

	@Test
	public void thirdPlaceCreditsTopThreeAndTopNine() {
		active(MasteryChatTracker.EVENT_WINNER, MasteryChatTracker.EVENT_TOP_3, MasteryChatTracker.EVENT_TOP_9);
		MasteryChatTracker.onMessage(STANDINGS, "SaltyHyper");
		assertEquals(0, currentOf(MasteryChatTracker.EVENT_WINNER), "third is not a win");
		assertEquals(1, currentOf(MasteryChatTracker.EVENT_TOP_3));
		assertEquals(1, currentOf(MasteryChatTracker.EVENT_TOP_9));
	}

	/** Fourth is out of the top 3 but still on the board. */
	@Test
	public void fourthPlaceCreditsTopNineOnly() {
		active(MasteryChatTracker.EVENT_WINNER, MasteryChatTracker.EVENT_TOP_3, MasteryChatTracker.EVENT_TOP_9);
		assertTrue(MasteryChatTracker.onMessage(STANDINGS, "Ophiliah"));
		assertEquals(0, currentOf(MasteryChatTracker.EVENT_TOP_3));
		assertEquals(1, currentOf(MasteryChatTracker.EVENT_TOP_9));
	}

	@Test
	public void ninthPlaceStillCreditsTopNine() {
		active(MasteryChatTracker.EVENT_TOP_9);
		assertTrue(MasteryChatTracker.onMessage(FULL_STANDINGS, "itfolds69"));
		assertEquals(1, currentOf(MasteryChatTracker.EVENT_TOP_9));
	}

	/** Not placing at all earns nothing, even though the message is a real result. */
	@Test
	public void missingFromTheStandingsEarnsNothing() {
		active(MasteryChatTracker.EVENT_WINNER, MasteryChatTracker.EVENT_TOP_3, MasteryChatTracker.EVENT_TOP_9);
		assertFalse(MasteryChatTracker.onMessage(STANDINGS, "Ophiliah_"));
		assertEquals(0, currentOf(MasteryChatTracker.EVENT_TOP_9));
	}

	/**
	 * The running board is posted every few minutes during an event and leads with
	 * "Current top players" — crediting it would award a placement per broadcast.
	 */
	@Test
	public void theInProgressLeaderboardIsInert() {
		active(MasteryChatTracker.EVENT_WINNER, MasteryChatTracker.EVENT_TOP_3, MasteryChatTracker.EVENT_TOP_9);
		assertFalse(MasteryChatTracker.onMessage(LIVE_BOARD, "iLikeCatsDotCom"));
		assertEquals(0, currentOf(MasteryChatTracker.EVENT_WINNER));
		assertEquals(0, currentOf(MasteryChatTracker.EVENT_TOP_9));
	}

	/** The bare conclusion notice carries no standings and must not credit. */
	@Test
	public void theConclusionNoticeAloneIsInert() {
		active(MasteryChatTracker.EVENT_TOP_9);
		assertFalse(MasteryChatTracker.onMessage(
				"Mini-Event » King Of The Hill Mini-Event has concluded!", "Ophiliah"));
		assertEquals(0, currentOf(MasteryChatTracker.EVENT_TOP_9));
	}

	/** A name that merely contains yours is a different player. */
	@Test
	public void standingsMatchTheWholeName() {
		active(MasteryChatTracker.EVENT_WINNER);
		assertFalse(MasteryChatTracker.onMessage(STANDINGS, "Kojee"));
		assertEquals(0, currentOf(MasteryChatTracker.EVENT_WINNER));
	}

	/** Percent floors the way the server does (54.78% renders as 54%). */
	@Test
	public void percentFloorsLikeServer() {
		assertEquals(54, MasteryQuest.percentOf(631076.685, 1152000));
		assertEquals(39, MasteryQuest.percentOf(319514.42, 806400));
		assertEquals(40, MasteryQuest.percentOf(6, 15));
		assertEquals(100, MasteryQuest.percentOf(1200000, 1152000), "overshoot clamps to 100");
	}
}
