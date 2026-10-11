package dev.jade.labsaddons.config;

import dev.jade.labsaddons.daily.DailyReset;
import dev.jade.labsaddons.daily.VoteTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** In the config package for {@code useStore}. The line is scenarios/vote-registered-three.jsonl. */
class VoteTrackerTest {
	private static final String VOTE = "MCLabs » Vote registered! Claim your vote rewards with /claimvotes";

	@TempDir
	Path configDir;

	@BeforeEach
	void freshConfig() {
		LabsAddonsConfig.useStore(new ConfigStore(configDir));
	}

	private static LabsAddonsConfig config() {
		return LabsAddonsConfig.get();
	}

	@Test
	void threeVoteLinesCountThree() {
		VoteTracker.onMessage(VOTE);
		VoteTracker.onMessage(VOTE);
		VoteTracker.onMessage(VOTE);
		assertEquals(3, VoteTracker.votesDone());
		assertTrue(VoteTracker.pending());
	}

	@Test
	void sevenVotesFinishTheDayAndMoreStayAtSeven() {
		for (int i = 0; i < 9; i++) {
			VoteTracker.onMessage(VOTE);
		}
		assertEquals(VoteTracker.VOTE_GOAL, VoteTracker.votesDone());
		assertFalse(VoteTracker.pending());
	}

	@Test
	void theLineSurvivesSurroundingWhitespaceAndCase() {
		VoteTracker.onMessage("\n" + VOTE + "\n");
		VoteTracker.onMessage(VOTE.toUpperCase());
		assertEquals(2, VoteTracker.votesDone());
	}

	@Test
	void aVoteLineQuotedInPlayerChatCountsNothing() {
		VoteTracker.onMessage("[S] [VIP] bob: " + VOTE);
		VoteTracker.onMessage("bob: " + VOTE);
		assertEquals(0, VoteTracker.votesDone());
		assertEquals(0, config().voteCount);
	}

	@Test
	void otherMcLabsLinesCountNothing() {
		VoteTracker.onMessage("MCLabs » Runner job posted: 2,176x Carrotenium at 70%.");
		VoteTracker.onMessage("MCLabs » You can vote again");
		assertEquals(0, VoteTracker.votesDone());
	}

	@Test
	void votesFromTheCurrentWindowAreKept() {
		config().voteBoundaryMs = DailyReset.currentBoundaryMs();
		config().voteCount = 5;
		assertEquals(5, VoteTracker.votesDone());
		VoteTracker.onMessage(VOTE);
		assertEquals(6, VoteTracker.votesDone());
	}

	@Test
	void aCountFromBeforeTheBoundaryReadsZero() {
		config().voteBoundaryMs = DailyReset.currentBoundaryMs() - 24 * 3_600_000L;
		config().voteCount = 5;
		assertEquals(0, VoteTracker.votesDone());
		assertTrue(VoteTracker.pending());
	}

	@Test
	void theFirstVoteAfterTheBoundaryRestartsAtOneAndStampsTheNewWindow() {
		config().voteBoundaryMs = DailyReset.currentBoundaryMs() - 24 * 3_600_000L;
		config().voteCount = 7;
		VoteTracker.onMessage(VOTE);
		assertEquals(1, VoteTracker.votesDone());
		assertEquals(DailyReset.currentBoundaryMs(), config().voteBoundaryMs);
	}

	@Test
	void aNegativeSavedCountReadsZero() {
		config().voteBoundaryMs = DailyReset.currentBoundaryMs();
		config().voteCount = -3;
		assertEquals(0, VoteTracker.votesDone());
	}

	@Test
	void markAllDoneFillsTheCurrentWindowOnly() {
		VoteTracker.markAllDone();
		assertEquals(VoteTracker.VOTE_GOAL, VoteTracker.votesDone());
		assertFalse(VoteTracker.pending());
		config().voteBoundaryMs -= 24 * 3_600_000L;
		assertEquals(0, VoteTracker.votesDone());
	}
}
