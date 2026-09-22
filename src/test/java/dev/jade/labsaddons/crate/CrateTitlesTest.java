package dev.jade.labsaddons.crate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The exact titles from all ten captured crates plus both voter screens. The title is the only
 * thing that claims a crate menu, so a miss here costs the real chest its texture and tooltips
 * with nothing drawn in their place.
 */
class CrateTitlesTest {
	/** Verbatim, one per captured dump. */
	private static final String[] SPINS = {
			"Opening a Favourites Crate...",
			"Opening a Mystery Crate...",
			"Opening a Spawner Crate...",
			"Opening a Summer Crate...",
			"Opening a Supply Crate I...",
			"Opening a Supply Crate II...",
			"Opening a Supply Crate III...",
			"Opening a Supply Crate IV...",
			"Opening a Supply Crate V...",
			"Opening a Tool Crate...",
	};

	@Test
	void everyCapturedSpinTitleIsRecognised() {
		for (String title : SPINS) {
			assertTrue(CrateTitles.isSpin(title), title);
		}
	}

	@Test
	void theCrateNameComesOutOfTheSpinTitle() {
		assertEquals("Supply Crate II", CrateTitles.crateName("Opening a Supply Crate II..."));
		assertEquals("Favourites Crate", CrateTitles.crateName("Opening a Favourites Crate..."));
		assertEquals("Tool Crate", CrateTitles.crateName("Opening a Tool Crate..."));
		// Nothing captured says "an", but a crate starting with a vowel is one rename away.
		assertEquals("Ancient Crate", CrateTitles.crateName("Opening an Ancient Crate..."));
	}

	@Test
	void aTitleThatIsAlreadyJustTheNameIsLeftAlone() {
		// The reward menu that follows the spin is titled with the bare crate name.
		assertEquals("Supply Crate II", CrateTitles.crateName("Supply Crate II"));
	}

	@Test
	void theRewardMenuIsNotMistakenForTheSpin() {
		// Same crate, but this one is browsable and must stay the server's own menu.
		assertFalse(CrateTitles.isSpin("Supply Crate II"));
		assertFalse(CrateTitles.isSpin("Your Exceedingly Rare odds"));
		assertFalse(CrateTitles.isSpin("Voter Crate"));
	}

	@Test
	void unrelatedAnimationScreensAreNotClaimed() {
		// Both are real MCLabs screens the mod already draws other boards over.
		assertFalse(CrateTitles.isSpin("Flipping a coin..."));
		assertFalse(CrateTitles.isSpin("Rolling rewards..."));
		assertFalse(CrateTitles.isSpin(null));
		assertFalse(CrateTitles.isSpin(""));
	}

	@Test
	void bothVoterScreensAreToldApart() {
		assertTrue(CrateTitles.isVoteRoll("Rolling rewards..."));
		assertFalse(CrateTitles.isVoteChoice("Rolling rewards..."));
		assertTrue(CrateTitles.isVoteChoice("Choose a reward!"));
		assertFalse(CrateTitles.isVoteRoll("Choose a reward!"));
		assertFalse(CrateTitles.isVoteRoll(null));
		assertFalse(CrateTitles.isVoteChoice(null));
	}
}
