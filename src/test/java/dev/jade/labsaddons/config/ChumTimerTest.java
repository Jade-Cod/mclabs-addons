package dev.jade.labsaddons.config;

import dev.jade.labsaddons.chum.ChumTimer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lines are from scenarios chum-purchase-then-chum-reply and chum-none-left.
 * Lives in the config package for {@code useStore}.
 */
class ChumTimerTest {
	private static final long SLACK_MS = 5_000L;
	private static final long MIN = 60_000L;

	private static final String PURCHASE = "♣ GCL » Ophiliah has just purchased 5 minutes of double fish for the whole lab!";
	private static final String REMAINING = "MCLabs » Double fish time remaining: 3m 31s";
	private static final String NONE = "MCLabs » You don't currently have double fish!";

	@TempDir
	Path configDir;

	@BeforeEach
	void freshConfig() {
		LabsAddonsConfig.useStore(new ConfigStore(configDir));
		ChumTimer.reset();
	}

	@Test
	void labPurchaseStartsTheTimer() {
		ChumTimer.onMessage(PURCHASE);
		assertTrue(ChumTimer.isActive());
		assertEquals(5 * MIN, ChumTimer.remainingMs(), SLACK_MS);
	}

	@Test
	void purchasesStackOnTheRemainingTime() {
		ChumTimer.onMessage(PURCHASE);
		ChumTimer.onMessage(PURCHASE.replace("5 minutes", "10 minutes"));
		assertEquals(15 * MIN, ChumTimer.remainingMs(), SLACK_MS);
	}

	@Test
	void purchaseOfAnHourCountsAsSixtyMinutes() {
		ChumTimer.onMessage(PURCHASE.replace("5 minutes", "1 hour"));
		assertEquals(60 * MIN, ChumTimer.remainingMs(), SLACK_MS);
	}

	@Test
	void zeroMinutePurchaseChangesNothing() {
		ChumTimer.onMessage(PURCHASE.replace("5 minutes", "0 minutes"));
		assertFalse(ChumTimer.isActive());
	}

	@Test
	void chumReplyReplacesTheLocalStack() {
		ChumTimer.onMessage(PURCHASE);
		ChumTimer.addChum();
		ChumTimer.onMessage(REMAINING);
		assertEquals(3 * MIN + 31_000L, ChumTimer.remainingMs(), SLACK_MS);
	}

	@Test
	void chumReplyCanRaiseTheTimerToo() {
		ChumTimer.onMessage(REMAINING);
		ChumTimer.onMessage("MCLabs » Double fish time remaining: 1h 2m 3s");
		assertEquals(62 * MIN + 3_000L, ChumTimer.remainingMs(), SLACK_MS);
	}

	@Test
	void purchaseAfterAReplyStacksOnTheServersValue() {
		ChumTimer.onMessage(REMAINING);
		ChumTimer.onMessage(PURCHASE);
		assertEquals(8 * MIN + 31_000L, ChumTimer.remainingMs(), SLACK_MS);
	}

	@Test
	void noDoubleFishReplyClearsTheTimer() {
		ChumTimer.onMessage(PURCHASE);
		ChumTimer.onMessage(NONE);
		assertFalse(ChumTimer.isActive());
		assertEquals(0L, ChumTimer.remainingMs());
		assertEquals(0L, LabsAddonsConfig.get().chumExpiryEpochMs);
	}

	@Test
	void expiredTimerReportsInactiveAndRestartsFromNow() {
		LabsAddonsConfig.get().chumExpiryEpochMs = System.currentTimeMillis() - 10 * MIN;
		assertFalse(ChumTimer.isActive());
		assertEquals(0L, ChumTimer.remainingMs());
		ChumTimer.onMessage(PURCHASE);
		assertEquals(5 * MIN, ChumTimer.remainingMs(), SLACK_MS);
	}

	@Test
	void addChumIsTwentyMinutes() {
		ChumTimer.addChum();
		assertEquals(20 * MIN, ChumTimer.remainingMs(), SLACK_MS);
	}

	@Test
	void formatRemainingRoundsUpToTheSecond() {
		ChumTimer.setRemaining(3 * MIN + 31_000L);
		assertEquals("3:31", ChumTimer.formatRemaining());
		ChumTimer.setRemaining(65 * MIN);
		assertEquals("65:00", ChumTimer.formatRemaining());
		ChumTimer.reset();
		assertEquals("0:00", ChumTimer.formatRemaining());
	}

	@Test
	void unrelatedLinesMoveNothing() {
		ChumTimer.onMessage("MCLabs » Vote registered! Claim your vote rewards with /claimvotes");
		ChumTimer.onMessage("Booster » Booster activated! All Chems boosted 2x for 30m.");
		assertFalse(ChumTimer.isActive());
	}
}
