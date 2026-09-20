package dev.jade.labsaddons.police;

import dev.jade.labsaddons.mastery.MasteryGains;
import dev.jade.labsaddons.prestige.PrestigeChem;
import dev.jade.labsaddons.prestige.PrestigeTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The confiscation lines, as the 2026-09-15..18 police logs recorded them. */
public class PoliceContrabandTest {
	@BeforeEach
	public void reset() {
		PrestigeTracker.clear();
		MasteryGains.clear();
		PrestigeTracker.merge(List.of(new PrestigeChem("Contraband III", 404725, 518400)));
	}

	private static double current() {
		return PrestigeTracker.chems().getFirst().current();
	}

	@Test
	public void anArrestAdvancesTheTierByTheStatedFigure() {
		assertTrue(PoliceContraband.onMessage("MCLPD » Earned 3,832 progress (x1.13) in confiscating "
				+ "contraband. Click to see your total progress."));
		assertEquals(404725 + 3832, current());
	}

	@Test
	public void aBountyChestEarnsAFractionAndStatesNoRate() {
		assertTrue(PoliceContraband.onMessage("Bounty » Earned 976.32 progress in confiscating "
				+ "contraband. Click to see your total progress."));
		assertEquals(404725 + 976.32, current(), 0.001);
	}

	@Test
	public void aFriskThatFindsNothingEarnsNothing() {
		assertFalse(PoliceContraband.onMessage("MCLPD » No contraband found on AtomicBomb."));
		assertFalse(PoliceContraband.onMessage("MCLPD » Small amount of contraband found on AtomicBomb. (2)"));
		assertFalse(PoliceContraband.onMessage("Arrest » fayebeeann was arrested with 3392 contraband by Ophiliah."));
		assertFalse(PoliceContraband.onMessage(
				"MCLPD » You confiscated 3,392 chems fayebeeann and earned $15,264 in commission!"));
		assertEquals(404725, current());
	}

	@Test
	public void onlyTheNearestUnmetTierMoves() {
		PrestigeTracker.merge(List.of(new PrestigeChem("Contraband IV", 404725, 691200)));
		PoliceContraband.onMessage("MCLPD » Earned 1,952 progress (x1.13) in confiscating contraband.");
		assertEquals(404725 + 1952, PrestigeTracker.chems().get(0).current());
		assertEquals(404725, PrestigeTracker.chems().get(1).current());
	}

	@Test
	public void aChemSaleIsNotAConfiscation() {
		assertFalse(PoliceContraband.onMessage(
				"» Earned prestige progress for Cactium and Potatium. (1.89x rate)"));
	}

	// --- the /prestige GUI, as message-24 dumped it ---

	@Test
	public void anUnmetTierReadsItsFigures() {
		PrestigeChem tier = PolicePrestigeReader.parseTier("✘ Police - Collect Contraband III",
				List.of("", "Goal:", "Confiscate 225 inventories of contraband.", "",
						"Progress:", "[|||||||||||||||||||||| 404725/518400 ||||||||||||||||||||||]",
						"", "Reward:", "● +1 Police Prestige Unlocked"));
		assertEquals("Contraband III", tier.chem());
		assertEquals(404725, tier.current());
		assertEquals(518400, tier.target());
	}

	@Test
	public void anUnlockedTierAndAChemSlotAreBothSkipped() {
		assertNull(PolicePrestigeReader.parseTier("✔ Police - Collect Contraband II",
				List.of("", "Description:", "You have confiscated 150 inventories of contraband.")));
		assertNull(PolicePrestigeReader.parseTier("✔ Chems - Sell Wheatium",
				List.of("", "Description:", "Prestige unlocked.")));
	}

	@Test
	public void aMetButUnTickedTierIsNotTracked() {
		assertNull(PolicePrestigeReader.parseTier("✘ Police - Collect Contraband III",
				List.of("Progress:", "[|||| 518400/518400 ||||]")));
	}
}
