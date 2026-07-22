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

	/** Percent floors the way the server does (54.78% renders as 54%). */
	@Test
	public void percentFloorsLikeServer() {
		assertEquals(54, MasteryQuest.percentOf(631076.685, 1152000));
		assertEquals(39, MasteryQuest.percentOf(319514.42, 806400));
		assertEquals(40, MasteryQuest.percentOf(6, 15));
		assertEquals(100, MasteryQuest.percentOf(1200000, 1152000), "overshoot clamps to 100");
	}
}
