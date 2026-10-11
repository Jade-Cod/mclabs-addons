package dev.jade.labsaddons.config;

import dev.jade.labsaddons.daily.DailyReset;
import dev.jade.labsaddons.daily.DailyTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** In the config package for {@code useStore}. Lines are the captured daily-spin-and-investor scenario plus the shapes DailyTracker documents. */
class DailyTrackerTest {
	private static final String SPIN = "Daily Spin » Daily bonus streak increased to 82 days! Spin again tomorrow to keep the streak alive.";
	private static final String SM_WAIT = "Investor Rewards » Your daily rewards will be available in 15h:24m!";
	private static final String SM_CLAIM = "Investor Rewards » You will receive your Daily Investor Rewards in 5 seconds...";
	private static final String SPIN_AGAIN = "Daily Spin » You've already used your daily spin today!";

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
	void freshConfigHasBothPending() {
		assertTrue(DailyTracker.dailyPending());
		assertTrue(DailyTracker.smPending());
	}

	@Test
	void typingSmClaimMarksItBeforeTheServerReplies() {
		DailyTracker.markSmClaimed();
		assertFalse(DailyTracker.smPending());
		assertEquals(System.currentTimeMillis(), config().smClaimedMs, 5_000L);
		assertTrue(DailyTracker.dailyPending());
	}

	@Test
	void capturedLinesQuotedInPlayerChatMarkNothing() {
		DailyTracker.onMessage("[S] [VIP] bob: " + SPIN);
		DailyTracker.onMessage("[S] [VIP] bob: " + SM_WAIT);
		assertTrue(DailyTracker.dailyPending());
		assertTrue(DailyTracker.smPending());
	}

	@Test
	void theStreakLineMarksTheSpinOnly() {
		DailyTracker.onMessage(SPIN);
		assertFalse(DailyTracker.dailyPending());
		assertTrue(DailyTracker.smPending());
	}

	@Test
	void theAlreadySpunLineAlsoMarksTheSpin() {
		DailyTracker.onMessage(SPIN_AGAIN);
		assertFalse(DailyTracker.dailyPending());
	}

	@Test
	void theInvestorWaitLineMarksTheClaimBecauseItMeansAlreadyClaimed() {
		DailyTracker.onMessage(SM_WAIT);
		assertFalse(DailyTracker.smPending());
		assertTrue(DailyTracker.dailyPending());
	}

	@Test
	void theConfirmLineMarksTheClaim() {
		DailyTracker.onMessage(SM_CLAIM);
		assertFalse(DailyTracker.smPending());
		assertTrue(DailyTracker.dailyPending());
	}

	@Test
	void anUnrelatedLineMarksNothing() {
		DailyTracker.onMessage("MCLabs » Vote registered! Claim your vote rewards with /claimvotes");
		DailyTracker.onMessage("Runner » Bob123 has completed your runner job, you've earned $5.53k.");
		assertEquals(0L, config().dailySpinClaimedMs);
		assertEquals(0L, config().smClaimedMs);
	}

	@Test
	void aClaimFromBeforeTheBoundaryIsPendingAgain() {
		long boundary = DailyReset.currentBoundaryMs();
		config().smClaimedMs = boundary - 1;
		config().dailySpinClaimedMs = boundary - 60_000L;
		assertTrue(DailyTracker.smPending());
		assertTrue(DailyTracker.dailyPending());
	}

	@Test
	void aClaimAtTheBoundaryCountsForTheNewCycle() {
		long boundary = DailyReset.currentBoundaryMs();
		config().smClaimedMs = boundary;
		config().dailySpinClaimedMs = boundary + 1;
		assertFalse(DailyTracker.smPending());
		assertFalse(DailyTracker.dailyPending());
	}
}
