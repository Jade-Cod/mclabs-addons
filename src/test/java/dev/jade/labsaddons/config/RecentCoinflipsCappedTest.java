package dev.jade.labsaddons.config;

import dev.jade.labsaddons.coinflip.CfPlayed;
import dev.jade.labsaddons.coinflip.CfStats;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The recent-flips list is trimmed as it is written, so the only way a longer one reaches
 * the lobby is off disk — a state.json from a future version, a hand-edit, a bad merge.
 * The rail stops drawing when it runs out of panel, so an over-long list does not spill
 * down the screen; it just gets copied in full on every frame the lobby is open, for rows
 * nobody will ever see.
 */
class RecentCoinflipsCappedTest {
	private static LabsAddonsConfig sanitized(LabsAddonsConfig config) throws Exception {
		Method sanitized = LabsAddonsConfig.class.getDeclaredMethod("sanitized");
		sanitized.setAccessible(true);
		return (LabsAddonsConfig) sanitized.invoke(config);
	}

	@Test
	void anOverLongListFromDiskIsTrimmedOnLoad() throws Exception {
		LabsAddonsConfig loaded = new LabsAddonsConfig();
		for (int i = 0; i < CfStats.RECENT_KEPT * 10; i++) {
			loaded.coinflipRecent.add(new CfPlayed(true, 100L, "Opponent" + i));
		}
		assertEquals(CfStats.RECENT_KEPT, sanitized(loaded).coinflipRecent.size());
	}

	@Test
	void theNewestFlipsAreTheOnesKept() throws Exception {
		LabsAddonsConfig loaded = new LabsAddonsConfig();
		for (int i = 0; i < CfStats.RECENT_KEPT * 2; i++) {
			loaded.coinflipRecent.add(new CfPlayed(true, 100L, "Opponent" + i));
		}
		// Newest first is how the list is written, so the cap has to take from the front.
		assertEquals("Opponent0", sanitized(loaded).coinflipRecent.get(0).opponent);
	}

	@Test
	void aShortListIsLeftAlone() throws Exception {
		LabsAddonsConfig loaded = new LabsAddonsConfig();
		loaded.coinflipRecent.add(new CfPlayed(false, -500L, "Nothing_but_fail"));
		assertEquals(1, sanitized(loaded).coinflipRecent.size());
	}
}
