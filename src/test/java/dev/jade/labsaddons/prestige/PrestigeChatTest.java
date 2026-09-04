package dev.jade.labsaddons.prestige;

import dev.jade.labsaddons.mastery.MasteryGains;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixtures are the real tooltips observed on MCLabs, verbatim — including the sale whose
 * two figures no client-side formula reproduces, which is the reason this reads the
 * server's numbers instead of computing them.
 */
public class PrestigeChatTest {
	/** As hovering a /prestige progress row renders it, second line included. */
	private static final String WHEATIUM = "Wheatium: 0/806,400\n(0% supplier)";
	private static final String CACTIUM = "Cactium: 412,880/1,382,400\n(0% supplier)";

	@BeforeEach
	public void reset() {
		PrestigeTracker.clear();
		MasteryGains.clear();
	}

	@Test
	public void aListedRowYieldsItsChemAndGoal() {
		List<PrestigeChem> parsed = PrestigeChat.parseListing(List.of(WHEATIUM));
		assertEquals(1, parsed.size());
		assertEquals("Wheatium", parsed.get(0).chem());
		assertEquals(0, parsed.get(0).current());
		assertEquals(806_400, parsed.get(0).target());
	}

	@Test
	public void groupedDigitsAreStripped() {
		PrestigeChem cactium = PrestigeChat.parseListing(List.of(CACTIUM)).get(0);
		assertEquals(412_880, cactium.current());
		assertEquals(1_382_400, cactium.target());
		assertEquals(29, cactium.percent());
	}

	/**
	 * Sweeberrium's goal text says 160 inventories, which at 2,304 per inventory would be
	 * 368,640 — every other chem matches that arithmetic exactly, but the server states
	 * 386,640. The stated number wins; deriving targets from the goal text would be wrong
	 * for this one chem.
	 */
	@Test
	public void theStatedTargetWinsOverWhatTheGoalTextImplies() {
		PrestigeChem parsed = PrestigeChat.parseListing(
				List.of("Sweeberrium: 0/386,640\n(0% supplier)")).get(0);
		assertEquals(386_640, parsed.target());
	}

	@Test
	public void aFullListingParsesEveryRow() {
		List<PrestigeChem> parsed = PrestigeChat.parseListing(List.of(
				WHEATIUM, "Betronium: 0/576,000", "Nethwartium: 0/806,400",
				"Potatium: 0/1,105,920", "Carrotenium: 0/1,036,800"));
		assertEquals(5, parsed.size());
		assertEquals("Carrotenium", parsed.get(4).chem());
	}

	@Test
	public void aTooltipWithoutAGoalIsIgnored() {
		assertTrue(PrestigeChat.parseListing(List.of("Click to view your stats")).isEmpty());
	}

	/** A zero target would divide by zero in the bar. */
	@Test
	public void aZeroTargetIsRejected() {
		assertTrue(PrestigeChat.parseListing(List.of("Wheatium: 0/0")).isEmpty());
	}

	/** The real sale: 3,264 Cactatonate-2-2-2 at 1.3x, split across its two base chems. */
	@Test
	public void aSaleHoverYieldsEachBaseChemsShare() {
		List<PrestigeChat.Gain> gains = PrestigeChat.parseEarned(
				List.of("Cactium x8,273\nPotatium x5,516"));
		assertEquals(2, gains.size());
		assertEquals("Cactium", gains.get(0).chem());
		assertEquals(8_273, gains.get(0).amount());
		assertEquals("Potatium", gains.get(1).chem());
		assertEquals(5_516, gains.get(1).amount());
	}

	/** Flattening a Component can drop the line breaks, running the entries together. */
	@Test
	public void saleEntriesParseWithoutLineBreaks() {
		List<PrestigeChat.Gain> gains = PrestigeChat.parseEarned(List.of("Cactium x8,273Potatium x5,516"));
		assertEquals(2, gains.size());
		assertEquals(8_273, gains.get(0).amount());
		assertEquals(5_516, gains.get(1).amount());
	}

	@Test
	public void aSaleOfOneBaseChemNamesOnlyThatChem() {
		List<PrestigeChat.Gain> gains = PrestigeChat.parseEarned(List.of("Wheatium x2,304"));
		assertEquals(1, gains.size());
		assertEquals("Wheatium", gains.get(0).chem());
	}

	// --- tracker ---

	@Test
	public void aSyncEstablishesTheTracks() {
		PrestigeTracker.merge(PrestigeChat.parseListing(List.of(WHEATIUM, CACTIUM)));
		assertEquals(2, PrestigeTracker.chems().size());
		assertTrue(PrestigeTracker.hasData());
	}

	@Test
	public void aSaleAdvancesAKnownChem() {
		PrestigeTracker.merge(PrestigeChat.parseListing(List.of(CACTIUM)));
		assertTrue(PrestigeTracker.advance("Cactium", 8_273));
		assertEquals(412_880 + 8_273, PrestigeTracker.chems().get(0).current());
	}

	/** The sale message's spelling need not match the listing's. */
	@Test
	public void chemMatchingIsCaseInsensitive() {
		PrestigeTracker.merge(PrestigeChat.parseListing(List.of(CACTIUM)));
		assertTrue(PrestigeTracker.advance("cactium", 100));
	}

	/**
	 * Without a synced goal there is no bar to fill, so an unknown chem is dropped rather
	 * than invented — {@link PrestigeStore} is what stops this from recurring each session.
	 */
	@Test
	public void anUnknownChemIsNotInvented() {
		assertFalse(PrestigeTracker.advance("Globerrium", 5_000));
		assertFalse(PrestigeTracker.hasData());
	}

	@Test
	public void progressStopsAtTheGoal() {
		PrestigeTracker.merge(List.of(new PrestigeChem("Wheatium", 806_000, 806_400)));
		assertTrue(PrestigeTracker.advance("Wheatium", 50_000));
		PrestigeChem wheatium = PrestigeTracker.chems().get(0);
		assertEquals(806_400, wheatium.current());
		assertTrue(wheatium.isComplete());
		assertEquals(100, wheatium.percent());
		// Already at the goal: nothing left to move, so no needless save.
		assertFalse(PrestigeTracker.advance("Wheatium", 1_000));
	}

	/**
	 * A completed chem may not appear in the listing at all — in the GUI it loses its
	 * progress line entirely — so a sync must not erase what it omits.
	 */
	@Test
	public void aSyncKeepsChemsItDoesNotMention() {
		PrestigeTracker.merge(List.of(new PrestigeChem("Wheatium", 806_400, 806_400)));
		PrestigeTracker.merge(PrestigeChat.parseListing(List.of(CACTIUM)));
		assertEquals(2, PrestigeTracker.chems().size());
		assertEquals(806_400, PrestigeTracker.chems().get(0).current());
	}

	@Test
	public void aSyncOverwritesWithTheServersFigures() {
		PrestigeTracker.merge(List.of(new PrestigeChem("Cactium", 999, 1_382_400)));
		PrestigeTracker.merge(PrestigeChat.parseListing(List.of(CACTIUM)));
		assertEquals(1, PrestigeTracker.chems().size());
		assertEquals(412_880, PrestigeTracker.chems().get(0).current());
	}

	@Test
	public void rowOrderFollowsTheServersListing() {
		PrestigeTracker.merge(PrestigeChat.parseListing(List.of(WHEATIUM, CACTIUM)));
		PrestigeTracker.merge(PrestigeChat.parseListing(List.of(CACTIUM, WHEATIUM)));
		assertEquals("Wheatium", PrestigeTracker.chems().get(0).chem());
	}

	// --- finished tracks ---

	/** Once a chem is done its hover reads "Pumpkonium: Complete" and states no figures. */
	@Test
	public void aFinishedTrackIsRecognisedWithoutFigures() {
		List<PrestigeChem> parsed = PrestigeChat.parseListing(List.of("Pumpkonium: Complete"));
		assertEquals(1, parsed.size());
		assertEquals("Pumpkonium", parsed.get(0).chem());
		assertTrue(parsed.get(0).isComplete());
		assertFalse(parsed.get(0).hasFigures());
		assertEquals(100, parsed.get(0).percent());
		assertEquals(1, parsed.get(0).fraction());
	}

	/** ChatPlus appends its own "Sent at ..." line to the tooltip; it must not confuse us. */
	@Test
	public void anExtraTooltipLineIsIgnored() {
		List<PrestigeChem> parsed = PrestigeChat.parseListing(
				List.of("Pumpkonium: Complete\nSent at 01:07:24 AM."));
		assertEquals(1, parsed.size());
		assertEquals("Pumpkonium", parsed.get(0).chem());
		assertTrue(parsed.get(0).isComplete());
	}

	/** A part-finished player's listing carries both shapes at once. */
	@Test
	public void aListingMayMixFinishedAndUnfinishedRows() {
		List<PrestigeChem> parsed = PrestigeChat.parseListing(List.of("Wheatium: Complete", CACTIUM));
		assertEquals(2, parsed.size());
		assertTrue(parsed.get(0).isComplete());
		assertFalse(parsed.get(1).isComplete());
	}

	/**
	 * The case this all exists for: a player whose saved progress is stale finishes the
	 * track, re-syncs, and must stop being credited for it. Without parsing "Complete"
	 * the stale figures would survive and the next sale would fire a phantom gain.
	 */
	@Test
	public void finishingATrackStopsFurtherGains() {
		PrestigeTracker.merge(PrestigeChat.parseListing(List.of(CACTIUM)));
		assertTrue(PrestigeTracker.advance("Cactium", 8_273));

		PrestigeTracker.merge(PrestigeChat.parseListing(List.of("Cactium: Complete")));
		MasteryGains.clear();

		assertFalse(PrestigeTracker.advance("Cactium", 8_273));
		assertEquals(0, MasteryGains.delta("Cactium"));
		assertTrue(PrestigeTracker.chems().get(0).isComplete());
	}

	/** A player done with every chem must never see a prestige notification again. */
	@Test
	public void aFullyFinishedPlayerGetsNoGains() {
		PrestigeTracker.merge(PrestigeChat.parseListing(
				List.of("Cactium: Complete", "Potatium: Complete")));
		assertFalse(PrestigeTracker.advance("Cactium", 8_273));
		assertFalse(PrestigeTracker.advance("Potatium", 5_516));
		assertFalse(MasteryGains.hasRecent());
	}

	/** Finishing keeps the goal we already knew, so the bar still renders full, not blank. */
	@Test
	public void aFinishedTrackKeepsAGoalItAlreadyHad() {
		PrestigeTracker.merge(PrestigeChat.parseListing(List.of(CACTIUM)));
		PrestigeTracker.merge(PrestigeChat.parseListing(List.of("Cactium: Complete")));
		PrestigeChem cactium = PrestigeTracker.chems().get(0);
		assertTrue(cactium.hasFigures());
		assertEquals(1_382_400, cactium.target());
		assertEquals(1_382_400, cactium.current());
	}

	/** The gain drives the widget's "+N" and its fade, so it must land in the registry. */
	@Test
	public void aSaleSurfacesAsALiveGain() {
		PrestigeTracker.merge(PrestigeChat.parseListing(List.of(CACTIUM)));
		PrestigeTracker.advance("cactium", 8_273);
		// Recorded under the chem's own spelling, which is what the widget renders.
		assertEquals(8_273, MasteryGains.delta("Cactium"));
		assertTrue(MasteryGains.recentNames().contains("Cactium"));
	}

	/** A bump clamped at the goal must report what landed, not what was asked for. */
	@Test
	public void aClampedGainReportsOnlyWhatLanded() {
		PrestigeTracker.merge(List.of(new PrestigeChem("Wheatium", 806_000, 806_400)));
		PrestigeTracker.advance("Wheatium", 50_000);
		assertEquals(400, MasteryGains.delta("Wheatium"));
	}

	@Test
	public void percentFloorsRatherThanRounds() {
		// 99.9% must not read as 100% while the track is still unfinished.
		assertEquals(99, new PrestigeChem("Wheatium", 806_399, 806_400).percent());
		assertFalse(new PrestigeChem("Wheatium", 806_399, 806_400).isComplete());
	}

	@Test
	public void anEmptyTrackReportsNoProgress() {
		assertEquals(0, new PrestigeChem("Wheatium", 0, 806_400).fraction());
		assertEquals(0, new PrestigeChem("Wheatium", 0, 806_400).percent());
	}
}
