package dev.jade.labsaddons.config;

import dev.jade.labsaddons.chem.ChemItems.ChemKey;
import dev.jade.labsaddons.chem.ChemtainerDepositCapture;
import dev.jade.labsaddons.chem.ChemtainerTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the capture through {@code tick(Map)} with per-chem inventory counts, so no
 * PlayerInventory is involved. In the config package for {@code useStore}, since a flush
 * credits {@link ChemtainerTracker}.
 */
class ChemtainerDepositCaptureTest {
	private static final ChemKey A = new ChemKey("chowartusite", "2-2-2");
	private static final ChemKey B = new ChemKey("whearootinide", "3-3-3");
	private static final int SETTLE_TICKS = 8;
	private static final int CONFIRM_TIMEOUT_TICKS = 60;
	private static final String DEPOSITED = "Deposited 1,723 chems into your chemtainer.";
	private static final String FULL = "MCLabs » Your Chemtainer is full, not all chems could be deposited!";

	@TempDir
	Path configDir;

	@BeforeEach
	void freshState() {
		LabsAddonsConfig.useStore(new ConfigStore(configDir));
		ChemtainerDepositCapture.cancel();
	}

	private static void ticks(int n, Map<ChemKey, Long> inventory) {
		for (int i = 0; i < n; i++) {
			ChemtainerDepositCapture.tick(inventory);
		}
	}

	private static long stored(ChemKey key) {
		return ChemtainerTracker.entries().stream()
				.filter(e -> e.chem.equals(key.chem()) && e.purity.equals(key.purity()))
				.mapToLong(e -> e.count).sum();
	}

	@Test
	void theLedgerGetsTheDropsOnceTheTotalMatches() {
		ChemtainerDepositCapture.arm(Map.of(A, 100L, B, 50L));
		ChemtainerDepositCapture.tick(Map.of(A, 40L, B, 50L));
		ChemtainerDepositCapture.noteDeposit(60);
		ticks(SETTLE_TICKS, Map.of(A, 40L, B, 50L));
		assertEquals(60L, stored(A));
		assertEquals(0L, stored(B));
	}

	@Test
	void nothingIsCreditedUntilTheSettleCountdownRunsOut() {
		ChemtainerDepositCapture.arm(Map.of(A, 100L));
		ChemtainerDepositCapture.tick(Map.of(A, 40L));
		ChemtainerDepositCapture.noteDeposit(60);
		ticks(SETTLE_TICKS - 1, Map.of(A, 40L));
		assertEquals(0L, stored(A));
		ChemtainerDepositCapture.tick(Map.of(A, 40L));
		assertEquals(60L, stored(A));
	}

	@Test
	void aLateDepositLineRestartsTheSettleCountdown() {
		ChemtainerDepositCapture.arm(Map.of(A, 100L));
		ChemtainerDepositCapture.tick(Map.of(A, 70L));
		ChemtainerDepositCapture.noteDeposit(30);
		ticks(SETTLE_TICKS - 2, Map.of(A, 70L));
		ChemtainerDepositCapture.tick(Map.of(A, 40L));
		ChemtainerDepositCapture.noteDeposit(30);
		ticks(SETTLE_TICKS - 1, Map.of(A, 40L));
		assertEquals(0L, stored(A));
		ChemtainerDepositCapture.tick(Map.of(A, 40L));
		assertEquals(60L, stored(A));
	}

	@Test
	void theServerTotalScalesTheDropsKeepingTheirProportion() {
		ChemtainerDepositCapture.arm(Map.of(A, 100L, B, 100L));
		ChemtainerDepositCapture.tick(Map.of(A, 40L, B, 80L));
		ChemtainerDepositCapture.noteDeposit(40);
		ticks(SETTLE_TICKS, Map.of(A, 40L, B, 80L));
		assertEquals(30L, stored(A));
		assertEquals(10L, stored(B));
	}

	@Test
	void scalingAlwaysSumsToTheServerTotalUsingLargestRemainder() {
		ChemtainerDepositCapture.arm(Map.of(A, 100L, B, 100L));
		ChemtainerDepositCapture.tick(Map.of(A, 40L, B, 80L));
		ChemtainerDepositCapture.noteDeposit(41);
		ticks(SETTLE_TICKS, Map.of(A, 40L, B, 80L));
		assertEquals(41L, ChemtainerTracker.totalChems());
		assertEquals(31L, stored(A));
		assertEquals(10L, stored(B));
	}

	@Test
	void aSameTickPickupThatHidesPartOfTheDropIsRecoveredFromTheTotal() {
		ChemtainerDepositCapture.arm(Map.of(A, 100L));
		ChemtainerDepositCapture.tick(Map.of(A, 60L));
		ChemtainerDepositCapture.noteDeposit(60);
		ticks(SETTLE_TICKS, Map.of(A, 60L));
		assertEquals(60L, stored(A));
	}

	@Test
	void aPickupBetweenDropsIsNotSubtractedFromTheDeposit() {
		ChemtainerDepositCapture.arm(Map.of(A, 100L));
		ChemtainerDepositCapture.tick(Map.of(A, 70L));
		ChemtainerDepositCapture.tick(Map.of(A, 90L));
		ChemtainerDepositCapture.tick(Map.of(A, 60L));
		ChemtainerDepositCapture.noteDeposit(50);
		ticks(SETTLE_TICKS, Map.of(A, 60L));
		assertEquals(50L, stored(A));
	}

	@Test
	void aChemThatAppearsAndThenDropsIsTracked() {
		ChemtainerDepositCapture.arm(Map.of(A, 100L));
		ChemtainerDepositCapture.tick(Map.of(A, 100L, B, 25L));
		ChemtainerDepositCapture.tick(Map.of(A, 100L));
		ChemtainerDepositCapture.noteDeposit(25);
		ticks(SETTLE_TICKS, Map.of(A, 100L));
		assertEquals(25L, stored(B));
	}

	@Test
	void aPhantomDropOfAnotherChemSharesTheServerTotalInsteadOfInflatingTheLedger() {
		// B left the inventory for a reason other than the deposit (eaten, dropped, traded).
		ChemtainerDepositCapture.arm(Map.of(A, 100L, B, 10L));
		ChemtainerDepositCapture.tick(Map.of(A, 40L));
		ChemtainerDepositCapture.noteDeposit(60);
		ticks(SETTLE_TICKS, Map.of(A, 40L));
		assertEquals(60L, ChemtainerTracker.totalChems());
		assertEquals(51L, stored(A));
		assertEquals(9L, stored(B));
	}

	@Test
	void theTotalLineWithCommasIsParsedFromChat() {
		ChemtainerDepositCapture.arm(Map.of(A, 2_000L));
		ChemtainerDepositCapture.tick(Map.of(A, 200L));
		ChemtainerTracker.onMessage(DEPOSITED);
		ticks(SETTLE_TICKS, Map.of(A, 200L));
		assertEquals(1_723L, stored(A));
	}

	@Test
	void twoDepositLinesAddUpAsOneTotal() {
		ChemtainerDepositCapture.arm(Map.of(A, 1_000L));
		ChemtainerDepositCapture.tick(Map.of(A, 400L));
		ChemtainerDepositCapture.noteDeposit(250);
		ChemtainerDepositCapture.noteDeposit(250);
		ticks(SETTLE_TICKS, Map.of(A, 400L));
		assertEquals(500L, stored(A));
	}

	@Test
	void aZeroTotalBanksNothing() {
		ChemtainerDepositCapture.arm(Map.of(A, 100L));
		ChemtainerDepositCapture.tick(Map.of(A, 40L));
		ChemtainerTracker.onMessage("Deposited 0 chems into your chemtainer.");
		ticks(SETTLE_TICKS, Map.of(A, 40L));
		assertTrue(ChemtainerTracker.entries().isEmpty());
	}

	@Test
	void aConfirmedDepositWithNoObservedDropBanksNothing() {
		ChemtainerDepositCapture.arm(Map.of(A, 100L));
		ChemtainerDepositCapture.noteDeposit(60);
		ticks(SETTLE_TICKS, Map.of(A, 100L));
		assertTrue(ChemtainerTracker.entries().isEmpty());
	}

	@Test
	void theFullLineWithNoNumberTrustsTheRawDrops() {
		ChemtainerDepositCapture.arm(Map.of(A, 100L, B, 50L));
		ChemtainerDepositCapture.tick(Map.of(A, 70L, B, 30L));
		ChemtainerTracker.onMessage(FULL);
		ticks(SETTLE_TICKS, Map.of(A, 70L, B, 30L));
		assertEquals(30L, stored(A));
		assertEquals(20L, stored(B));
	}

	@Test
	void aTotalAndAFullLineTogetherUseTheTotal() {
		ChemtainerDepositCapture.arm(Map.of(A, 100L));
		ChemtainerDepositCapture.tick(Map.of(A, 70L));
		ChemtainerTracker.onMessage(FULL);
		ChemtainerTracker.onMessage("Deposited 25 chems into your chemtainer.");
		ticks(SETTLE_TICKS, Map.of(A, 70L));
		assertEquals(25L, stored(A));
	}

	@Test
	void theCompanyLineNeverConfirmsADepositSoTheArmTimesOutAndCreditsNothing() {
		ChemtainerDepositCapture.arm(Map.of(A, 100L));
		ChemtainerDepositCapture.tick(Map.of(A, 40L));
		ChemtainerTracker.onMessage("Companies » Deposited 1,723 chems into company chemtainer.");
		ticks(CONFIRM_TIMEOUT_TICKS + SETTLE_TICKS, Map.of(A, 40L));
		assertTrue(ChemtainerTracker.entries().isEmpty());
	}

	@Test
	void anUnconfirmedArmIsDroppedAfterTheTimeoutAndLaterLinesAreIgnored() {
		ChemtainerDepositCapture.arm(Map.of(A, 100L));
		ChemtainerDepositCapture.tick(Map.of(A, 40L));
		ticks(CONFIRM_TIMEOUT_TICKS, Map.of(A, 40L));
		ChemtainerDepositCapture.noteDeposit(60);
		ticks(SETTLE_TICKS * 2, Map.of(A, 40L));
		assertTrue(ChemtainerTracker.entries().isEmpty());
	}

	@Test
	void anArmStillWaitingJustInsideTheTimeoutCanBeConfirmed() {
		ChemtainerDepositCapture.arm(Map.of(A, 100L));
		ChemtainerDepositCapture.tick(Map.of(A, 40L));
		ticks(CONFIRM_TIMEOUT_TICKS - 2, Map.of(A, 40L));
		ChemtainerDepositCapture.noteDeposit(60);
		ticks(SETTLE_TICKS, Map.of(A, 40L));
		assertEquals(60L, stored(A));
	}

	@Test
	void cancelThrowsAwayTheCapture() {
		ChemtainerDepositCapture.arm(Map.of(A, 100L));
		ChemtainerDepositCapture.tick(Map.of(A, 40L));
		ChemtainerDepositCapture.noteDeposit(60);
		ChemtainerDepositCapture.cancel();
		ticks(SETTLE_TICKS * 2, Map.of(A, 40L));
		assertTrue(ChemtainerTracker.entries().isEmpty());
	}

	@Test
	void linesWhenNotArmedDoNothingAndDoNotArmAnything() {
		ChemtainerDepositCapture.noteDeposit(60);
		ChemtainerDepositCapture.noteFull();
		ChemtainerDepositCapture.arm(Map.of(A, 100L));
		ChemtainerDepositCapture.tick(Map.of(A, 40L));
		ticks(SETTLE_TICKS * 2, Map.of(A, 40L));
		assertTrue(ChemtainerTracker.entries().isEmpty());
	}

	@Test
	void armingAgainWhileActiveKeepsTheFirstBaselineAndExtendsTheWindow() {
		ChemtainerDepositCapture.arm(Map.of(A, 100L));
		ChemtainerDepositCapture.tick(Map.of(A, 70L));
		ChemtainerDepositCapture.arm(Map.of(A, 70L));
		ChemtainerDepositCapture.tick(Map.of(A, 40L));
		ChemtainerDepositCapture.noteDeposit(60);
		ticks(SETTLE_TICKS, Map.of(A, 40L));
		assertEquals(60L, stored(A));
	}

	@Test
	void aFlushedCaptureIsOverSoTheNextTickAndLineAreIgnored() {
		ChemtainerDepositCapture.arm(Map.of(A, 100L));
		ChemtainerDepositCapture.tick(Map.of(A, 40L));
		ChemtainerDepositCapture.noteDeposit(60);
		ticks(SETTLE_TICKS, Map.of(A, 40L));
		ChemtainerDepositCapture.noteDeposit(60);
		ticks(SETTLE_TICKS * 2, Map.of(A, 0L));
		assertEquals(60L, stored(A));
	}

	@Test
	void aSecondDepositAfterTheFirstFlushedIsCapturedFresh() {
		ChemtainerDepositCapture.arm(Map.of(A, 100L));
		ChemtainerDepositCapture.tick(Map.of(A, 40L));
		ChemtainerDepositCapture.noteDeposit(60);
		ticks(SETTLE_TICKS, Map.of(A, 40L));
		ChemtainerDepositCapture.arm(Map.of(A, 40L));
		ChemtainerDepositCapture.tick(Map.of(A, 10L));
		ChemtainerDepositCapture.noteDeposit(30);
		ticks(SETTLE_TICKS, Map.of(A, 10L));
		assertEquals(90L, stored(A));
	}
}
