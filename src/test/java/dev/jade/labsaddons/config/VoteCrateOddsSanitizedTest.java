package dev.jade.labsaddons.config;

import dev.jade.labsaddons.crate.VoteOddsEntry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The odds table is the one thing on the voter board that is taken on trust from disk, and a
 * part-written entry would put a figure against the wrong reward — which is worse here than
 * showing no figure at all, because the whole point is deciding which of three to keep.
 */
class VoteCrateOddsSanitizedTest {
	private static LabsAddonsConfig sanitized(LabsAddonsConfig config) throws Exception {
		Method sanitized = LabsAddonsConfig.class.getDeclaredMethod("sanitized");
		sanitized.setAccessible(true);
		return (LabsAddonsConfig) sanitized.invoke(config);
	}

	@Test
	void aGoodEntrySurvivesTheRoundTrip() throws Exception {
		LabsAddonsConfig loaded = new LabsAddonsConfig();
		loaded.voteCrateOdds.add(new VoteOddsEntry("Voter Crate", "$3,000", 7.6d));
		LabsAddonsConfig clean = sanitized(loaded);
		assertEquals(1, clean.voteCrateOdds.size());
		assertEquals(7.6d, clean.voteCrateOdds.get(0).chance);
	}

	@Test
	void anEntryMissingEitherNameIsDropped() throws Exception {
		LabsAddonsConfig loaded = new LabsAddonsConfig();
		loaded.voteCrateOdds.add(new VoteOddsEntry("", "$3,000", 7.6d));
		loaded.voteCrateOdds.add(new VoteOddsEntry("Voter Crate", "  ", 7.6d));
		loaded.voteCrateOdds.add(new VoteOddsEntry(null, null, 7.6d));
		assertEquals(0, sanitized(loaded).voteCrateOdds.size());
	}

	@Test
	void anImpossibleChanceIsDropped() throws Exception {
		LabsAddonsConfig loaded = new LabsAddonsConfig();
		loaded.voteCrateOdds.add(new VoteOddsEntry("Voter Crate", "Zero", 0d));
		loaded.voteCrateOdds.add(new VoteOddsEntry("Voter Crate", "Negative", -4d));
		assertEquals(0, sanitized(loaded).voteCrateOdds.size());
	}

	@Test
	void aNullListFromAHandEditedFileIsNotAThrow() throws Exception {
		LabsAddonsConfig loaded = new LabsAddonsConfig();
		loaded.voteCrateOdds = null;
		assertEquals(0, sanitized(loaded).voteCrateOdds.size());
	}
}
