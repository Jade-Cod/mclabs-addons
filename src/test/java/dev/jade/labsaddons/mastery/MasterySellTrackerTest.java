package dev.jade.labsaddons.mastery;

import dev.jade.labsaddons.chem.ChemItems.ChemKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sale maths, exercised through the {@code credit} seam with plain maps so no
 * Minecraft session is needed. Figures come from a real sale in the logs:
 * {@code » You've sold 3,392 chems} / {@code (1.89x rate)}.
 */
public class MasterySellTrackerTest {
	private static final String DEALER = "White Dealer";
	private static final String SELL_CHOWART = "Sell Chowartusite";
	private static final String SELL_TO_DEALER = "Sell to White Dealer";
	private static final String SELL_CACTATONATE = "Sell Cactatonate";
	private static final double RATE = 1.89;
	/** Tier 2 -> 1.30x, so one chem at 1.89x is worth 2.457 — three decimals, as observed. */
	private static final ChemKey CHOWART_T2 = new ChemKey("chowartusite", "2-2-2");
	private static final ChemKey CHOWART_T0 = new ChemKey("chowartusite", "3-0-3");
	private static final ChemKey CHORBER_T3 = new ChemKey("chorberrium", "3-3-3");
	/** Base crops are plain vanilla items and carry no purity component at all. */
	private static final ChemKey WHEAT = new ChemKey("wheatium", "");
	private static final ChemKey NO_SATCHEL = null;

	@AfterEach
	public void reset() {
		MasteryTracker.clear();
		MasteryGains.clear();
		MasterySellTracker.reset();
	}

	private static void active(String... names) {
		MasteryTracker.setQuests(Arrays.stream(names)
				.map(n -> new MasteryQuest(null, n, 0, 10_000_000, 0))
				.toList());
	}

	private static double currentOf(String name) {
		return MasteryTracker.quests().stream()
				.filter(q -> q.name().equals(name))
				.findFirst().orElseThrow().current();
	}

	// --- the purity tier table ---

	@Test
	public void progressTierReadsTheMiddleField() {
		assertEquals(2, MasterySellTracker.progressTier("2-2-2"));
		assertEquals(0, MasterySellTracker.progressTier("3-0-3"), "middle field, not the ends");
		assertEquals(3, MasterySellTracker.progressTier("1-3-0"));
	}

	/** A base crop has no purity, and Mastery does not count it — -1 is the answer, not a failure. */
	@Test
	public void anAbsentPurityHasNoTier() {
		assertEquals(-1, MasterySellTracker.progressTier(""));
		assertEquals(-1, MasterySellTracker.progressTier(null));
		assertEquals(-1, MasterySellTracker.progressTier("2-2"));
		assertEquals(-1, MasterySellTracker.progressTier("2-x-2"));
	}

	/** An unseen tier earns nothing rather than an extrapolated multiplier. */
	@Test
	public void aTierBeyondTheTableIsNotGuessed() {
		assertEquals(-1, MasterySellTracker.progressTier("0-4-0"));
		assertEquals(-1, MasterySellTracker.progressTier("0--1-0"));
	}

	@Test
	public void theMultiplierTableMatchesTheServer() {
		assertEquals(1.00, MasterySellTracker.PROGRESS_MULTIPLIER[0]);
		assertEquals(1.15, MasterySellTracker.PROGRESS_MULTIPLIER[1]);
		assertEquals(1.30, MasterySellTracker.PROGRESS_MULTIPLIER[2]);
		assertEquals(1.50, MasterySellTracker.PROGRESS_MULTIPLIER[3]);
	}

	// --- the formula ---

	@Test
	public void aSingleStackIsCountTimesRateTimesTier() {
		active(SELL_CHOWART);
		assertTrue(MasterySellTracker.credit(Map.of(CHOWART_T2, 1000L), 1000, RATE, null, NO_SATCHEL));
		assertEquals(1000 * 1.89 * 1.30, currentOf(SELL_CHOWART), 1e-6);
	}

	/** The dealer challenge takes the whole sale, the chem challenge only its own share. */
	@Test
	public void theDealerChallengeSumsEveryChem() {
		active(SELL_CHOWART, SELL_TO_DEALER);
		MasterySellTracker.credit(Map.of(CHOWART_T2, 100L, CHORBER_T3, 200L), 300, RATE, DEALER, NO_SATCHEL);
		double chowart = 100 * 1.89 * 1.30;
		double chorber = 200 * 1.89 * 1.50;
		assertEquals(chowart, currentOf(SELL_CHOWART), 1e-6);
		assertEquals(chowart + chorber, currentOf(SELL_TO_DEALER), 1e-6);
	}

	/** Same chem at two purities: each stack carries its own multiplier, then they add. */
	@Test
	public void oneChemAtTwoPuritiesSumsPerTier() {
		active(SELL_CHOWART);
		MasterySellTracker.credit(Map.of(CHOWART_T2, 100L, CHOWART_T0, 100L), 200, RATE, null, NO_SATCHEL);
		assertEquals(100 * 1.89 * 1.30 + 100 * 1.89 * 1.00, currentOf(SELL_CHOWART), 1e-6);
	}

	/** Mastery is compounds only — a base crop earns nothing even in a counted sale. */
	@Test
	public void baseCropsEarnNoMastery() {
		active("Sell Wheatium", SELL_TO_DEALER);
		assertFalse(MasterySellTracker.credit(Map.of(WHEAT, 3000L), 3000, RATE, DEALER, NO_SATCHEL));
		assertEquals(0, currentOf("Sell Wheatium"));
		assertEquals(0, currentOf(SELL_TO_DEALER));
	}

	/** ...but they still fill the server's total, so they must not inflate the remainder. */
	@Test
	public void baseCropsStillConsumeTheReportedTotal() {
		active(SELL_CHOWART);
		// 3,000 wheat + 392 satchel chems = the 3,392 the server reported.
		MasterySellTracker.credit(Map.of(WHEAT, 3000L), 3392, RATE, null, CHOWART_T2);
		assertEquals(392 * 1.89 * 1.30, currentOf(SELL_CHOWART), 1e-6);
	}

	@Test
	public void noRateMeansNoCredit() {
		active(SELL_CHOWART);
		assertFalse(MasterySellTracker.credit(Map.of(CHOWART_T2, 1000L), 1000, 0, DEALER, NO_SATCHEL));
		assertEquals(0, currentOf(SELL_CHOWART));
	}

	/** An unattributed sale still credits the chem challenges it can identify. */
	@Test
	public void anUnknownDealerDoesNotBlockChemCredit() {
		active(SELL_CHOWART, SELL_TO_DEALER);
		assertTrue(MasterySellTracker.credit(Map.of(CHOWART_T2, 50L), 50, RATE, null, NO_SATCHEL));
		assertEquals(50 * 1.89 * 1.30, currentOf(SELL_CHOWART), 1e-6);
		assertEquals(0, currentOf(SELL_TO_DEALER), "no dealer to attribute it to");
	}

	// --- the satchel remainder ---

	/** What the diff never saw is the server's total minus what it did see. */
	@Test
	public void theSatchelRemainderIsCreditedToItsOwnChem() {
		active(SELL_CHOWART, "Sell Chorberrium");
		MasterySellTracker.credit(Map.of(CHOWART_T2, 2304L), 3392, RATE, null, CHORBER_T3);
		assertEquals(2304 * 1.89 * 1.30, currentOf(SELL_CHOWART), 1e-6);
		assertEquals(1088 * 1.89 * 1.50, currentOf("Sell Chorberrium"), 1e-6);
	}

	/** Never opened the satchel: credit what we saw and let the scrape find the rest. */
	@Test
	public void anUnknownSatchelDropsTheRemainder() {
		active(SELL_CHOWART, SELL_TO_DEALER);
		MasterySellTracker.credit(Map.of(CHOWART_T2, 2304L), 3392, RATE, DEALER, NO_SATCHEL);
		double seen = 2304 * 1.89 * 1.30;
		assertEquals(seen, currentOf(SELL_CHOWART), 1e-6);
		assertEquals(seen, currentOf(SELL_TO_DEALER), 1e-6);
	}

	/** A diff bigger than the reported total means no satchel moved — not negative progress. */
	@Test
	public void aNegativeRemainderIsIgnored() {
		active(SELL_CHOWART);
		MasterySellTracker.credit(Map.of(CHOWART_T2, 500L), 400, RATE, null, CHOWART_T2);
		assertEquals(500 * 1.89 * 1.30, currentOf(SELL_CHOWART), 1e-6);
	}

	/** A satchel of base crops is still no Mastery, however large the remainder. */
	@Test
	public void aBaseCropSatchelEarnsNothing() {
		active("Sell Wheatium");
		assertFalse(MasterySellTracker.credit(Map.of(), 3392, RATE, null, WHEAT));
		assertEquals(0, currentOf("Sell Wheatium"));
	}

	// --- the rate line ---

	/**
	 * Both lines are verbatim from a real pair of sales three minutes apart: the server
	 * prints the multiplier only when there is one to print. Reading the bare line as
	 * "no rate" is what made the first build credit nothing at all.
	 */
	@Test
	public void aBarePrestigeLineMeansTheBaseRate() {
		assertEquals(1.0, MasterySellTracker.rateFrom(
				"» Earned prestige progress for Cactium and Potatium."));
	}

	@Test
	public void aBoostedPrestigeLineCarriesItsMultiplier() {
		assertEquals(1.31, MasterySellTracker.rateFrom(
				"» Earned prestige progress for Cactium and Potatium. (1.31x rate)"));
	}

	@Test
	public void anUnrelatedLineHasNoRate() {
		assertEquals(-1, MasterySellTracker.rateFrom("» You've sold 3,264 chems for $102k"));
		assertEquals(-1, MasterySellTracker.rateFrom(null));
	}

	/** The sale that produced no HUD movement, priced end to end. */
	@Test
	public void theRealSaleCreditsInventoryAndSatchelTogether() {
		active(SELL_CACTATONATE);
		ChemKey cactatonate = new ChemKey("cactatonate", "2-2-2");
		// 2,112 carried + 1,152 in the satchel = the 3,264 the server reported.
		assertTrue(MasterySellTracker.credit(
				Map.of(cactatonate, 2112L), 3264, 1.31, null, cactatonate));
		assertEquals(3264 * 1.31 * 1.30, currentOf(SELL_CACTATONATE), 1e-6);
	}

	// --- dealer names ---

	@Test
	public void dealerNamePicksOutTheNameplate() {
		assertEquals("White Dealer", MasterySellTracker.dealerName("White Dealer"));
		assertEquals("Traveling Dealer", MasterySellTracker.dealerName("Traveling Dealer"));
		assertEquals("Red Dealer", MasterySellTracker.dealerName("§cRed Dealer"));
	}

	@Test
	public void nonDealersAreNotArmed() {
		assertNull(MasterySellTracker.dealerName("Jade"));
		assertNull(MasterySellTracker.dealerName("Dealer's Assistant"));
		assertNull(MasterySellTracker.dealerName(null));
	}

	/** An unselected challenge earns nothing — the shared advance() seam enforces it. */
	@Test
	public void anInactiveChallengeIsNotCredited() {
		active("Kill Nyx");
		assertFalse(MasterySellTracker.credit(Map.of(CHOWART_T2, 1000L), 1000, RATE, DEALER, NO_SATCHEL));
	}
}
