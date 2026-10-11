package dev.jade.labsaddons.config;

import dev.jade.labsaddons.chem.ChemItems.ChemKey;
import dev.jade.labsaddons.chem.ChemtainerDepositCapture;
import dev.jade.labsaddons.chem.ChemtainerEntry;
import dev.jade.labsaddons.chem.ChemtainerTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Chemtainer ledger. Lines are scenarios/chemtainer-withdraw-and-full and chemtainer-company-deposit, plus the personal deposit line from ChemtainerDepositLineTest. */
class ChemtainerTrackerTest {
	private static final ChemKey WHEAT = new ChemKey("whearootinide", "3-3-3");
	private static final ChemKey CHOW = new ChemKey("chowartusite", "2-2-2");
	private static final String WITHDREW = "MCLabs » Withdrew 2,112 Whearootinide-3-3-3 from your chemtainer.";
	private static final String COMPANY = "Companies » Deposited 1,723 chems into company chemtainer.";

	@TempDir
	Path configDir;

	@BeforeEach
	void freshConfig() {
		LabsAddonsConfig.useStore(new ConfigStore(configDir));
		ChemtainerDepositCapture.cancel();
	}

	private static long countOf(ChemKey key) {
		return ChemtainerTracker.entries().stream()
				.filter(e -> e.chem.equals(key.chem()) && e.purity.equals(key.purity()))
				.mapToLong(e -> e.count).sum();
	}

	@Test
	void aDepositCreditsANewEntryWithItsLabel() {
		ChemtainerTracker.applyDeposit(Map.of(CHOW, 300L));
		ChemtainerEntry entry = ChemtainerTracker.entries().get(0);
		assertEquals("chowartusite", entry.chem);
		assertEquals("2-2-2", entry.purity);
		assertEquals("Chowartusite-2-2-2", entry.label);
		assertEquals(300L, entry.count);
		assertTrue(ChemtainerTracker.hasSnapshot());
	}

	@Test
	void aSecondDepositAddsToTheSameEntry() {
		ChemtainerTracker.applyDeposit(Map.of(CHOW, 300L));
		ChemtainerTracker.applyDeposit(Map.of(CHOW, 200L));
		assertEquals(1, ChemtainerTracker.entries().size());
		assertEquals(500L, countOf(CHOW));
		assertEquals(500L, ChemtainerTracker.totalChems());
	}

	@Test
	void differentPuritiesStayApart() {
		ChemtainerTracker.applyDeposit(Map.of(new ChemKey("chowartusite", "2-2-2"), 10L,
				new ChemKey("chowartusite", "3-3-3"), 20L, new ChemKey("chowartusite", ""), 30L));
		assertEquals(3, ChemtainerTracker.entries().size());
		assertEquals(60L, ChemtainerTracker.totalChems());
	}

	@Test
	void theWithdrewLineDebitsTheLedger() {
		ChemtainerTracker.applyDeposit(Map.of(WHEAT, 5_000L));
		ChemtainerTracker.onMessage(WITHDREW);
		assertEquals(5_000L - 2_112L, countOf(WHEAT));
	}

	@Test
	void withdrawingEverythingRemovesTheEntry() {
		ChemtainerTracker.applyDeposit(Map.of(WHEAT, 2_112L));
		ChemtainerTracker.onMessage(WITHDREW);
		assertTrue(ChemtainerTracker.entries().isEmpty());
		assertNull(ChemtainerTracker.largestChem());
	}

	@Test
	void withdrawingMoreThanTheLedgerHoldsRemovesItRatherThanGoingNegative() {
		ChemtainerTracker.applyDeposit(Map.of(WHEAT, 100L));
		ChemtainerTracker.onMessage(WITHDREW);
		assertTrue(ChemtainerTracker.entries().isEmpty());
	}

	@Test
	void withdrawingAChemWeNeverSawAddsNothing() {
		ChemtainerTracker.applyDeposit(Map.of(CHOW, 100L));
		ChemtainerTracker.onMessage(WITHDREW);
		assertEquals(List.of(CHOW), ChemtainerTracker.entries().stream()
				.map(e -> new ChemKey(e.chem, e.purity)).toList());
		assertEquals(100L, countOf(CHOW));
	}

	@Test
	void aWithdrawOfAChemWithNoPurityMatchesTheEntryWithNone() {
		ChemKey plain = new ChemKey("wheatium", "");
		ChemtainerTracker.applyDeposit(Map.of(plain, 1_500L));
		ChemtainerTracker.onMessage("MCLabs » Withdrew 1,000 Wheatium from your chemtainer.");
		assertEquals(500L, countOf(plain));
	}

	@Test
	void theCompanyDepositLineMovesNothingEvenWhenCaptureIsNotArmed() {
		ChemtainerTracker.applyDeposit(Map.of(CHOW, 100L));
		ChemtainerTracker.onMessage(COMPANY);
		assertEquals(100L, ChemtainerTracker.totalChems());
	}

	@Test
	void aWithdrawLineQuotedInPlayerChatMovesNothing() {
		ChemtainerTracker.applyDeposit(Map.of(WHEAT, 5_000L));
		ChemtainerTracker.onMessage("[S] [VIP] bob: " + WITHDREW);
		assertEquals(5_000L, countOf(WHEAT));
	}

	@Test
	void aSnapshotReplacesTheLedgerAndCancelsAnInFlightCapture() {
		ChemtainerTracker.applyDeposit(Map.of(CHOW, 100L));
		ChemtainerDepositCapture.arm(Map.of(CHOW, 100L));
		ChemtainerDepositCapture.tick(Map.of(CHOW, 40L));
		ChemtainerDepositCapture.noteDeposit(60);
		ChemtainerTracker.snapshot(List.of(new ChemtainerEntry("chowartusite", "2-2-2", "Chowartusite-2-2-2", 700L)));
		for (int i = 0; i < 20; i++) {
			ChemtainerDepositCapture.tick(Map.of(CHOW, 40L));
		}
		assertEquals(700L, countOf(CHOW));
	}

	@Test
	void largestChemIsTheOneWithTheMostStored() {
		ChemtainerTracker.applyDeposit(Map.of(CHOW, 10L, WHEAT, 99L));
		assertEquals(WHEAT, ChemtainerTracker.largestChem());
	}

	@Test
	void clearEmptiesTheLedgerAndTheSnapshotTime() {
		ChemtainerTracker.applyDeposit(Map.of(CHOW, 10L));
		ChemtainerTracker.clear();
		assertTrue(ChemtainerTracker.entries().isEmpty());
		assertFalse(ChemtainerTracker.hasSnapshot());
	}
}
